package com.util.demo2.user.service;

import com.util.demo2.auth.JwtUtil;
import com.util.demo2.auth.LoginUser;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.Result;
import com.util.demo2.user.dto.LoginRequest;
import com.util.demo2.user.dto.UserSaveRequest;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import com.util.demo2.user.vo.LoginVO;
import com.util.demo2.user.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 业务规则单元测试：用 Mockito 假造 UserRepository，不需要数据库和 Spring 容器。
 * 覆盖登录各分支、密码加密、提权防护、角色与班级一致性、删除护栏等"线上出问题最难查"的逻辑。
 *
 * 运行：mvnw.cmd test -Dtest=UserServiceTest
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, jwtUtil, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：返回 token、角色、id")
    void loginSuccess() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        when(jwtUtil.generate(1L)).thenReturn("fake-token");

        LoginVO vo = userService.login(loginRequest("admin", "123456"));

        assertEquals("fake-token", vo.getToken());
        assertEquals("admin", vo.getRole());
        assertEquals(1L, vo.getId().longValue());
    }

    @Test
    @DisplayName("密码错误返回 401，且提示不暴露账号是否存在")
    void loginWrongPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("admin", "wrong-password")));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertEquals("账号或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("账号不存在返回与密码错误完全相同的 401 提示")
    void loginAccountNotExists() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("ghost", "123456")));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertEquals("账号或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("账号被禁用时密码正确也不能登录")
    void loginDisabledAccount() {
        when(userRepository.findByUsername("stu")).thenReturn(Optional.of(user(2L, "stu", "123456", "student", 0, 1L)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("stu", "123456")));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("账号前后有空格时自动去掉再查（用户手输常见）")
    void loginTrimsUsername() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        when(jwtUtil.generate(1L)).thenReturn("fake-token");

        LoginVO vo = userService.login(loginRequest("  admin  ", "123456"));

        assertEquals("fake-token", vo.getToken());
        assertEquals("admin", vo.getUsername());
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("新增用户：密码加密成 BCrypt，状态默认启用")
    void createEncodesPassword() {
        when(userRepository.existsClass(1L)).thenReturn(true);
        when(userRepository.existsUsername("s001")).thenReturn(false);
        when(userRepository.existsUsernameIgnoreDeleted("s001")).thenReturn(false);
        when(userRepository.insert(any(User.class))).thenReturn(10L);

        assertEquals(10L, userService.create(saveRequest("s001", "student")).longValue());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        User saved = captor.getValue();

        assertTrue(saved.getPassword().startsWith("$2"), "库里必须是 BCrypt 哈希，不能是明文");
        assertTrue(passwordEncoder.matches("123456", saved.getPassword()), "哈希必须能校验回原密码");
        assertEquals(1, saved.getStatus().intValue());
        assertEquals(1L, saved.getClassId().longValue());
    }

    @Test
    @DisplayName("新增学生不填班级：允许（班级模块未合入前需要先建人再分班），class_id 存 NULL")
    void createAllowsStudentWithoutClass() {
        UserSaveRequest request = saveRequest("s001", "student");
        request.setClassId(null);
        when(userRepository.existsUsername("s001")).thenReturn(false);
        when(userRepository.existsUsernameIgnoreDeleted("s001")).thenReturn(false);
        when(userRepository.insert(any(User.class))).thenReturn(10L);

        assertEquals(10L, userService.create(request).longValue());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        assertNull(captor.getValue().getClassId());
    }

    @Test
    @DisplayName("新增教师/管理员：classId 被强制清空（带班关系由 class.teacher_id 表示）")
    void createClearsClassIdForTeacher() {
        UserSaveRequest request = saveRequest("t001", "teacher");
        request.setClassId(9L);
        when(userRepository.existsClass(9L)).thenReturn(true);
        when(userRepository.existsUsername("t001")).thenReturn(false);
        when(userRepository.existsUsernameIgnoreDeleted("t001")).thenReturn(false);
        when(userRepository.insert(any(User.class))).thenReturn(11L);

        userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        assertNull(captor.getValue().getClassId(), "教师不应挂在班级下");
    }

    @Test
    @DisplayName("新增时班级不存在：拒绝")
    void createRejectsMissingClass() {
        when(userRepository.existsClass(99L)).thenReturn(false);
        UserSaveRequest request = saveRequest("s001", "student");
        request.setClassId(99L);

        BusinessException e = assertThrows(BusinessException.class, () -> userService.create(request));

        assertTrue(e.getMessage().contains("班级不存在"));
    }

    @Test
    @DisplayName("账号重复被拒绝")
    void createRejectsDuplicateUsername() {
        when(userRepository.existsClass(1L)).thenReturn(true);
        when(userRepository.existsUsername("s001")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest("s001", "student")));

        assertTrue(e.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("账号被已删除用户占用时给出可读提示（不是 500 唯一键冲突）")
    void createRejectsUsernameOccupiedByDeletedUser() {
        when(userRepository.existsClass(1L)).thenReturn(true);
        when(userRepository.existsUsername("s001")).thenReturn(false);
        when(userRepository.existsUsernameIgnoreDeleted("s001")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest("s001", "student")));

        assertTrue(e.getMessage().contains("已删除"));
    }

    @Test
    @DisplayName("非法角色被拒绝")
    void createRejectsIllegalRole() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest("s002", "superman")));

        assertTrue(e.getMessage().contains("角色"));
    }

    @Test
    @DisplayName("密码少于 6 位被拒绝")
    void createRejectsShortPassword() {
        UserSaveRequest request = saveRequest("s003", "student");
        request.setPassword("123");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.create(request));

        assertTrue(e.getMessage().contains("密码"));
    }

    @Test
    @DisplayName("账号为空 / 姓名为空被拒绝")
    void createRejectsBlankUsernameAndRealName() {
        UserSaveRequest blankUsername = saveRequest("s004", "student");
        blankUsername.setUsername("   ");
        assertTrue(assertThrows(BusinessException.class, () -> userService.create(blankUsername))
                .getMessage().contains("账号"));

        UserSaveRequest blankRealName = saveRequest("s005", "student");
        blankRealName.setRealName("  ");
        assertTrue(assertThrows(BusinessException.class, () -> userService.create(blankRealName))
                .getMessage().contains("姓名"));
    }

    // ==================== 修改：越权与提权防护 ====================

    @Test
    @DisplayName("学生把自己改成 admin 被 403 拦住")
    void updateRejectsPrivilegeEscalation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(2L, "s001", "张三", "student", 1L));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("admin");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.update(2L, request));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
        verify(userRepository, never()).update(any(User.class));
    }

    @Test
    @DisplayName("学生修改别人的资料被 403 拦住")
    void updateRejectsOtherUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(3L, "s002", "李四", "student", 1L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.update(2L, new UserSaveRequest()));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
    }

    @Test
    @DisplayName("学生偷偷改自己的 status/classId：403 而不是静默忽略")
    void updateRejectsSelfStatusAndClassChange() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(2L, "s001", "张三", "student", 1L));

        UserSaveRequest changeStatus = new UserSaveRequest();
        changeStatus.setStatus(0);
        assertEquals(Result.CODE_FORBIDDEN,
                assertThrows(BusinessException.class, () -> userService.update(2L, changeStatus)).getCode());

        UserSaveRequest changeClass = new UserSaveRequest();
        changeClass.setClassId(99L);
        assertEquals(Result.CODE_FORBIDDEN,
                assertThrows(BusinessException.class, () -> userService.update(2L, changeClass)).getCode());
    }

    @Test
    @DisplayName("学生改自己的姓名：允许，且角色/状态保持原样")
    void updateSelfRealNameAllowed() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(2L, "s001", "张三", "student", 1L));

        UserSaveRequest request = new UserSaveRequest();
        request.setRealName("张三丰");
        userService.update(2L, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals("张三丰", captor.getValue().getRealName());
        assertEquals("student", captor.getValue().getRole());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals(1L, captor.getValue().getClassId().longValue());
    }

    @Test
    @DisplayName("登录账号不允许修改：传了不同的账号直接 400，不静默忽略")
    void updateRejectsUsernameChange() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("s002");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.update(2L, request));

        assertTrue(e.getMessage().contains("账号不允许修改"));
        verify(userRepository, never()).update(any(User.class));
    }

    @Test
    @DisplayName("前端编辑表单原样回填 username：允许保存")
    void updateAllowsSameUsername() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("s001");
        request.setRealName("张三丰");
        userService.update(2L, request);

        verify(userRepository).update(any(User.class));
    }

    // ==================== 修改：密码 ====================

    @Test
    @DisplayName("新密码过短必须在写库之前就被拒绝（不能出现\"报了 400 但资料已改\"）")
    void updateRejectsShortPasswordBeforeWriting() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRealName("张三丰");
        request.setPassword("123");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.update(2L, request));

        assertTrue(e.getMessage().contains("密码"));
        verify(userRepository, never()).update(any(User.class));
        verify(userRepository, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("修改时密码留空/不传：不改密码")
    void updateKeepsPasswordWhenBlank() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest blank = new UserSaveRequest();
        blank.setPassword("");
        userService.update(2L, blank);

        userService.update(2L, new UserSaveRequest());

        verify(userRepository, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员重置密码：新密码同样加密入库")
    void updateResetsPasswordEncrypted() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setPassword("new-pass-123");
        userService.update(2L, request);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(anyLong(), hash.capture());
        assertTrue(hash.getValue().startsWith("$2"), "库里必须是 BCrypt 哈希");
        assertTrue(passwordEncoder.matches("new-pass-123", hash.getValue()));
    }

    // ==================== 修改：角色与班级一致性 ====================

    @Test
    @DisplayName("把教师改成学生、暂时不填班级：允许（class_id 存 NULL，等分班后再补）")
    void updateAllowsDemoteToStudentWithoutClass() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "t001", "123456", "teacher", 1, null)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("student");
        userService.update(3L, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals("student", captor.getValue().getRole());
        assertNull(captor.getValue().getClassId());
    }

    @Test
    @DisplayName("把教师改成学生并指定班级：允许")
    void updateAllowsStudentWithClass() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "t001", "123456", "teacher", 1, null)));
        when(userRepository.existsClass(5L)).thenReturn(true);
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("student");
        request.setClassId(5L);
        userService.update(3L, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals("student", captor.getValue().getRole());
        assertEquals(5L, captor.getValue().getClassId().longValue());
    }

    @Test
    @DisplayName("学生被改成教师后：class_id 自动清空")
    void updateClearsClassIdWhenRoleIsNoLongerStudent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("teacher");
        userService.update(2L, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals("teacher", captor.getValue().getRole());
        assertNull(captor.getValue().getClassId(), "教师不应挂在班级下");
    }

    @Test
    @DisplayName("管理员改班级：班级必须真实存在")
    void updateRejectsMissingClass() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        when(userRepository.existsClass(99L)).thenReturn(false);
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setClassId(99L);

        assertTrue(assertThrows(BusinessException.class, () -> userService.update(2L, request))
                .getMessage().contains("班级不存在"));
    }

    // ==================== 修改：管理员护栏 ====================

    @Test
    @DisplayName("管理员不能禁用自己的账号")
    void updateRejectsDisableSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setStatus(0);

        assertTrue(assertThrows(BusinessException.class, () -> userService.update(1L, request))
                .getMessage().contains("自己"));
    }

    @Test
    @DisplayName("不能把最后一个管理员降级为其他角色")
    void updateRejectsDemoteLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        when(userRepository.countByRole("admin")).thenReturn(1L);
        LoginUser.set(new LoginUser(9L, "admin2", "管理员二号", "admin", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("teacher");

        assertTrue(assertThrows(BusinessException.class, () -> userService.update(1L, request))
                .getMessage().contains("最后一个管理员"));
        verify(userRepository, never()).update(any(User.class));
    }

    // ==================== 查询单个用户 ====================

    @Test
    @DisplayName("查自己：允许；查别人：403；管理员查任何人：允许")
    void getByIdPermission() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));

        LoginUser.set(new LoginUser(2L, "s001", "张三", "student", 1L));
        UserVO self = userService.getById(2L);
        assertEquals("s001", self.getUsername());
        assertEquals("student", self.getRole());

        LoginUser.set(new LoginUser(3L, "s002", "李四", "student", 1L));
        assertEquals(Result.CODE_FORBIDDEN,
                assertThrows(BusinessException.class, () -> userService.getById(2L)).getCode());

        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));
        assertEquals("s001", userService.getById(2L).getUsername());
    }

    @Test
    @DisplayName("用户不存在或已被删除：400 而不是 500")
    void getByIdNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        BusinessException e = assertThrows(BusinessException.class, () -> userService.getById(99L));

        assertEquals(Result.CODE_PARAM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("不存在"));
    }

    // ==================== 逻辑删除 ====================

    @Test
    @DisplayName("不能删除当前登录的账号")
    void deleteRejectsSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        BusinessException e = assertThrows(BusinessException.class, () -> userService.delete(1L));

        assertTrue(e.getMessage().contains("不能删除当前登录"));
    }

    @Test
    @DisplayName("不能删除最后一个管理员")
    void deleteRejectsLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1, null)));
        when(userRepository.countByRole("admin")).thenReturn(1L);
        LoginUser.set(new LoginUser(9L, "admin2", "管理员二号", "admin", null));

        BusinessException e = assertThrows(BusinessException.class, () -> userService.delete(1L));

        assertTrue(e.getMessage().contains("最后一个管理员"));
    }

    @Test
    @DisplayName("删除普通用户走逻辑删除（不是物理删除）")
    void deleteNormalUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1, 1L)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        userService.delete(2L);

        verify(userRepository).logicDelete(2L);
    }

    @Test
    @DisplayName("删除不存在的用户：400")
    void deleteNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(Result.CODE_PARAM_ERROR,
                assertThrows(BusinessException.class, () -> userService.delete(99L)).getCode());
    }

    // ==================== 测试数据工厂 ====================

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    /** 学生默认带上班级 1，教师/管理员不带班级（和库里的约束一致） */
    private static UserSaveRequest saveRequest(String username, String role) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setPassword("123456");
        request.setRealName("张三");
        request.setRole(role);
        if (LoginUser.ROLE_STUDENT.equals(role)) {
            request.setClassId(1L);
        }
        return request;
    }

    private User user(long id, String username, String rawPassword, String role, int status, Long classId) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRealName("测试用户");
        u.setRole(role);
        u.setStatus(status);
        u.setClassId(classId);
        return u;
    }
}
