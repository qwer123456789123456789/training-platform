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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 业务规则单元测试：用 Mockito 假造 UserRepository，不需要数据库和 Spring 容器。
 * 覆盖登录各分支、密码加密、提权防护、删除护栏等"线上出问题最难查"的逻辑。
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

    @Test
    @DisplayName("登录成功：返回 token、角色、id")
    void loginSuccess() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1)));
        when(jwtUtil.generate(1L)).thenReturn("fake-token");

        LoginVO vo = userService.login(loginRequest("admin", "123456"));

        assertEquals("fake-token", vo.getToken());
        assertEquals("admin", vo.getRole());
        assertEquals(1L, vo.getId().longValue());
    }

    @Test
    @DisplayName("密码错误返回 401，且提示不暴露账号是否存在")
    void loginWrongPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1)));

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
        when(userRepository.findByUsername("stu")).thenReturn(Optional.of(user(2L, "stu", "123456", "student", 0)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("stu", "123456")));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("新增用户：密码加密成 BCrypt，状态默认启用")
    void createEncodesPassword() {
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
    }

    @Test
    @DisplayName("账号重复被拒绝")
    void createRejectsDuplicateUsername() {
        when(userRepository.existsUsername("s001")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest("s001", "student")));

        assertTrue(e.getMessage().contains("已存在"));
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
    @DisplayName("学生把自己改成 admin 被 403 拦住")
    void updateRejectsPrivilegeEscalation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1)));
        LoginUser.set(new LoginUser(2L, "s001", "张三", "student", null));

        UserSaveRequest request = new UserSaveRequest();
        request.setRole("admin");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.update(2L, request));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
    }

    @Test
    @DisplayName("学生修改别人的资料被 403 拦住")
    void updateRejectsOtherUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1)));
        LoginUser.set(new LoginUser(3L, "s002", "李四", "student", null));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.update(2L, new UserSaveRequest()));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
    }

    @Test
    @DisplayName("不能删除当前登录的账号")
    void deleteRejectsSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        BusinessException e = assertThrows(BusinessException.class, () -> userService.delete(1L));

        assertTrue(e.getMessage().contains("不能删除当前登录"));
    }

    @Test
    @DisplayName("不能删除最后一个管理员")
    void deleteRejectsLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "123456", "admin", 1)));
        when(userRepository.countByRole("admin")).thenReturn(1L);
        LoginUser.set(new LoginUser(9L, "admin2", "管理员二号", "admin", null));

        BusinessException e = assertThrows(BusinessException.class, () -> userService.delete(1L));

        assertTrue(e.getMessage().contains("最后一个管理员"));
    }

    @Test
    @DisplayName("删除普通用户走逻辑删除")
    void deleteNormalUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "123456", "student", 1)));
        LoginUser.set(new LoginUser(1L, "admin", "管理员", "admin", null));

        userService.delete(2L);

        verify(userRepository).logicDelete(2L);
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static UserSaveRequest saveRequest(String username, String role) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setPassword("123456");
        request.setRealName("张三");
        request.setRole(role);
        return request;
    }

    private User user(long id, String username, String rawPassword, String role, int status) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRealName("测试用户");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }
}
