package com.util.demo2.user.service;

import com.util.demo2.auth.AuthInterceptor;
import com.util.demo2.auth.JwtUtil;
import com.util.demo2.auth.LoginUser;
import com.util.demo2.auth.RequireRole;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
import com.util.demo2.user.dto.LoginRequest;
import com.util.demo2.user.dto.UserSaveRequest;
import com.util.demo2.user.repository.UserRepository;
import com.util.demo2.user.vo.LoginVO;
import com.util.demo2.user.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户模块业务链路测试 —— <b>真连 MySQL</b>（Spring 容器 + 真 UserRepository + 真 JdbcTemplate）。
 *
 * 与 Mock 单测的分工：
 *   - Mock 单测（UserServiceTest / UserServicePageTest / AuthInterceptorTest / UserApiContractTest）验证业务分支，秒级、不依赖环境；
 *   - 本类验证"在真库上跑得通"：密码真加密进库、登录真能校验、修改真落库、逻辑删除真生效、
 *     禁用/删除后旧 token 真的立刻失效（拦截器 + 真库联动）。
 *
 * 数据安全：类上 @Transactional，每个用例结束自动回滚；用例自己造班级和用户，
 * 不读、不改真库里已有的数据（只有"admin 能登录"和"最后一个管理员护栏"两条会读 admin 行，但不会改它）。
 *
 * 前置条件：本机 MySQL 已启动 + 已执行 database/create-database.sql。
 * 运行：mvnw.cmd test -Dtest=UserServiceDbTest
 */
@SpringBootTest
@Transactional
class UserServiceDbTest {

    private static final String RUN = "it_" + Long.toString(System.currentTimeMillis() % 1000000, 36);
    private static final String RAW_PASSWORD = "123456";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 大部分用例要调"只有管理员能调"的 Service 方法（新增/修改/删除），
     * 这里统一放一个管理员身份进 ThreadLocal；id 用负数，保证不会和任何真实用户撞成"改自己"。
     */
    @BeforeEach
    void loginAsAdmin() {
        LoginUser.set(new LoginUser(-1L, "it_admin", "集成测试管理员", LoginUser.ROLE_ADMIN, null));
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    // ==================== 登录（真库里的哈希） ====================

    @Test
    @DisplayName("内置管理员登录：用真库里的 BCrypt 哈希校验通过，返回三段式 token")
    void adminCanLoginWithStoredHash() {
        LoginVO vo = userService.login(loginRequest("admin", "123456"));

        assertNotNull(vo.getToken());
        assertEquals(3, vo.getToken().split("\\.").length, "token 必须是 header.payload.signature");
        assertEquals("admin", vo.getRole());
        assertEquals(jwtUtil.parseUserId(vo.getToken()), vo.getId(), "token 里带的必须是这个用户的 id");
    }

    @Test
    @DisplayName("密码错误 / 账号不存在：都是 401 且提示完全相同（不暴露账号是否存在）")
    void loginFailuresAreIndistinguishable() {
        BusinessException wrongPassword = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("admin", "wrong-password")));
        BusinessException noSuchUser = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest(RUN + "_ghost", RAW_PASSWORD)));

        assertEquals(Result.CODE_UNAUTHORIZED, wrongPassword.getCode());
        assertEquals(wrongPassword.getMessage(), noSuchUser.getMessage());
    }

    @Test
    @DisplayName("新增的用户：密码真的以 BCrypt 落库，并且能立刻登录成功")
    void createdUserCanLoginAndPasswordIsHashed() {
        Long classId = insertClass();
        Long id = userService.create(saveRequest(RUN + "_s1", "student", classId, RAW_PASSWORD));

        String stored = jdbcTemplate.queryForObject("SELECT password FROM `user` WHERE id = ?", String.class, id);
        assertTrue(stored.startsWith("$2"), "库里必须是 BCrypt 哈希，实际是：" + stored);
        assertNotEquals(RAW_PASSWORD, stored, "绝不能存明文");
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, stored));

        LoginVO vo = userService.login(loginRequest(RUN + "_s1", RAW_PASSWORD));
        assertEquals(id, vo.getId());
        assertEquals(classId, vo.getClassId(), "学生登录要带回班级 id（前端要用）");
    }

    @Test
    @DisplayName("账号被禁用后：登录被 401 拦住（库里的 status 真的被改了）")
    void disabledUserCannotLogin() {
        Long id = userService.create(saveRequest(RUN + "_s2", "student", insertClass(), RAW_PASSWORD));

        UserSaveRequest disable = new UserSaveRequest();
        disable.setStatus(0);
        userService.update(id, disable);

        assertEquals(0, jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", Integer.class, id).intValue());
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest(RUN + "_s2", RAW_PASSWORD)));
        assertTrue(e.getMessage().contains("禁用"));
    }

    // ==================== 新增（唯一索引、班级、角色） ====================

    @Test
    @DisplayName("账号重复：在真库唯一索引下也返回 400 可读提示，而不是 500")
    void duplicateUsernameRejected() {
        Long classId = insertClass();
        userService.create(saveRequest(RUN + "_s3", "student", classId, RAW_PASSWORD));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest(RUN + "_s3", "student", classId, RAW_PASSWORD)));

        assertEquals(Result.CODE_PARAM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("班级不存在：拒绝（existsClass 真的查了 class 表）")
    void missingClassRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.create(saveRequest(RUN + "_s4", "student", 99999999L, RAW_PASSWORD)));

        assertTrue(e.getMessage().contains("班级不存在"));
    }

    @Test
    @DisplayName("新增教师：class_id 落库为 SQL NULL")
    void teacherHasNullClassId() {
        Long id = userService.create(saveRequest(RUN + "_t1", "teacher", insertClass(), RAW_PASSWORD));

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE id = ? AND class_id IS NULL", Integer.class, id);
        assertEquals(1, nullCount.intValue(), "教师不该挂在班级下");
    }

    // ==================== 修改（真落库） ====================

    @Test
    @DisplayName("改姓名 + 重置密码：都真落库；新密码可登录、旧密码失效")
    void updatePersistsAndPasswordResetWorks() {
        Long id = userService.create(saveRequest(RUN + "_s5", "student", insertClass(), RAW_PASSWORD));

        UserSaveRequest rename = new UserSaveRequest();
        rename.setRealName("新名字");
        rename.setPassword("new-pass-123");
        userService.update(id, rename);

        assertEquals("新名字", jdbcTemplate.queryForObject("SELECT real_name FROM `user` WHERE id = ?", String.class, id));
        assertEquals(id, userService.login(loginRequest(RUN + "_s5", "new-pass-123")).getId());
        assertThrows(BusinessException.class, () -> userService.login(loginRequest(RUN + "_s5", RAW_PASSWORD)));
    }

    @Test
    @DisplayName("密码传空串：库里哈希一个字符都不变，旧密码仍能登录")
    void blankPasswordKeepsStoredHash() {
        Long id = userService.create(saveRequest(RUN + "_s6", "student", insertClass(), RAW_PASSWORD));
        String before = jdbcTemplate.queryForObject("SELECT password FROM `user` WHERE id = ?", String.class, id);

        UserSaveRequest rename = new UserSaveRequest();
        rename.setRealName("只改名字");
        rename.setPassword("");
        userService.update(id, rename);

        assertEquals(before, jdbcTemplate.queryForObject("SELECT password FROM `user` WHERE id = ?", String.class, id));
        assertEquals(id, userService.login(loginRequest(RUN + "_s6", RAW_PASSWORD)).getId());
    }

    @Test
    @DisplayName("新密码过短：400 且资料一个字都没被改（回归：曾经「报了错却改了数据」）")
    void shortPasswordDoesNotTouchDatabase() {
        Long id = userService.create(saveRequest(RUN + "_s7", "student", insertClass(), RAW_PASSWORD));
        String realNameBefore = jdbcTemplate.queryForObject("SELECT real_name FROM `user` WHERE id = ?", String.class, id);
        String passwordBefore = jdbcTemplate.queryForObject("SELECT password FROM `user` WHERE id = ?", String.class, id);

        UserSaveRequest request = new UserSaveRequest();
        request.setRealName("不该被改的名字");
        request.setPassword("123");
        assertThrows(BusinessException.class, () -> userService.update(id, request));

        assertEquals(realNameBefore,
                jdbcTemplate.queryForObject("SELECT real_name FROM `user` WHERE id = ?", String.class, id),
                "校验失败时姓名不能被改");
        assertEquals(passwordBefore, jdbcTemplate.queryForObject("SELECT password FROM `user` WHERE id = ?", String.class, id));
    }

    @Test
    @DisplayName("不能降级 / 不能删除最后一个管理员（本机库管理员数量为 1 时生效）")
    void lastAdminIsProtected() {
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE role = 'admin' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        Assumptions.assumeTrue(userRepository.countByRole("admin") == 1,
                "本机库里有多个管理员，这条护栏不适用，跳过");

        UserSaveRequest demote = new UserSaveRequest();
        demote.setRole("student");
        assertTrue(assertThrows(BusinessException.class, () -> userService.update(adminId, demote))
                .getMessage().contains("最后一个管理员"));
        assertTrue(assertThrows(BusinessException.class, () -> userService.delete(adminId))
                .getMessage().contains("最后一个管理员"));

        assertEquals("admin",
                jdbcTemplate.queryForObject("SELECT role FROM `user` WHERE id = ?", String.class, adminId),
                "护栏生效时库里不能有任何变化");
    }

    // ==================== 分页（真库查询） ====================

    @Test
    @DisplayName("分页查询真库：账号模糊 + 角色过滤 + 逻辑删除用户不再出现 + 参数夹紧")
    void pageQueriesRealDatabase() {
        Long classId = insertClass();
        Long keep = userService.create(saveRequest(RUN + "_pg1", "student", classId, RAW_PASSWORD));
        userService.create(saveRequest(RUN + "_pg2", "student", classId, RAW_PASSWORD));
        userService.create(saveRequest(RUN + "_pg3", "teacher", classId, RAW_PASSWORD));

        PageResult<UserVO> all = userService.page(1, 10, RUN + "_pg", null, null, null);
        assertEquals(3L, all.getTotal().longValue());
        assertEquals(RUN + "_pg3", all.getRecords().get(0).getUsername(), "列表按 id 倒序（最新建的在前）");
        assertNull(all.getRecords().get(0).getClassName(), "老师不挂班级，所以班级名是 null");
        assertEquals(RUN + "_班级", all.getRecords().get(1).getClassName(), "学生的 LEFT JOIN 班级名要带出来");

        PageResult<UserVO> students = userService.page(1, 10, RUN + "_pg", "student", null, null);
        assertEquals(2L, students.getTotal().longValue());

        // 按班级筛人：只有两个学生挂了班级（教师那条 class_id 被强制成 NULL，符合"教师不挂班级"的约定）
        PageResult<UserVO> inClass = userService.page(1, 10, RUN + "_pg", null, classId, 1);
        assertEquals(2L, inClass.getTotal().longValue());

        userService.delete(keep);
        PageResult<UserVO> afterDelete = userService.page(1, 10, RUN + "_pg", null, null, null);
        assertEquals(2L, afterDelete.getTotal().longValue(), "逻辑删除的用户不该再出现在列表里");
        assertTrue(afterDelete.getRecords().stream().noneMatch(v -> v.getUsername().equals(RUN + "_pg1")));

        PageResult<UserVO> clamped = userService.page(0, 999999, RUN + "_pg", null, null, null);
        assertEquals(1L, clamped.getCurrent().longValue());
        assertEquals(100L, clamped.getSize().longValue());
    }

    // ==================== 逻辑删除（真库） ====================

    @Test
    @DisplayName("逻辑删除：行还在库里（is_deleted=1），查不到、登不上、再删报 400")
    void logicDeleteKeepsRowButBlocksUse() {
        Long id = userService.create(saveRequest(RUN + "_s8", "student", insertClass(), RAW_PASSWORD));

        userService.delete(id);

        assertEquals(1, jdbcTemplate.queryForObject("SELECT is_deleted FROM `user` WHERE id = ?", Integer.class, id).intValue());
        assertTrue(userRepository.findById(id).isEmpty());
        assertTrue(assertThrows(BusinessException.class, () -> userService.login(loginRequest(RUN + "_s8", RAW_PASSWORD)))
                .getMessage().contains("账号或密码错误"));
        assertTrue(assertThrows(BusinessException.class, () -> userService.delete(id))
                .getMessage().contains("不存在"));
    }

    // ==================== 旧 token 立即失效（拦截器 + 真库联动） ====================

    @Test
    @DisplayName("真库签发的 token 过拦截器：放行并写入 LoginUser")
    void interceptorPassesWithRealToken() {
        Long classId = insertClass();
        Long id = userService.create(saveRequest(RUN + "_s9", "student", classId, RAW_PASSWORD));
        LoginUser.clear();

        assertTrue(authInterceptor.preHandle(requestWith(jwtUtil.generate(id)),
                new MockHttpServletResponse(), loginOnlyHandler()));
        assertEquals(id, LoginUser.currentUserId());
        assertEquals("student", LoginUser.currentRole());
        assertEquals(classId, LoginUser.get().getClassId());
    }

    @Test
    @DisplayName("账号被禁用 / 被逻辑删除后，旧 token 立刻 401（不用等 token 过期）")
    void oldTokenDiesAfterDisableOrDelete() {
        Long id = userService.create(saveRequest(RUN + "_s10", "student", insertClass(), RAW_PASSWORD));
        String token = jwtUtil.generate(id);

        // 1) token 本来是有效的
        LoginUser.clear();
        assertTrue(authInterceptor.preHandle(requestWith(token), new MockHttpServletResponse(), loginOnlyHandler()));

        // 2) 管理员禁用 -> 旧 token 立即失效
        LoginUser.clear();
        LoginUser.set(new LoginUser(-1L, "it_admin", "集成测试管理员", LoginUser.ROLE_ADMIN, null));
        UserSaveRequest disable = new UserSaveRequest();
        disable.setStatus(0);
        userService.update(id, disable);
        LoginUser.clear();
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> authInterceptor.preHandle(requestWith(token), new MockHttpServletResponse(), loginOnlyHandler()));
        assertEquals(Result.CODE_UNAUTHORIZED, disabled.getCode());
        assertTrue(disabled.getMessage().contains("禁用"));

        // 3) 重新启用 -> token 又能用（角色与状态每次都回查数据库）
        LoginUser.set(new LoginUser(-1L, "it_admin", "集成测试管理员", LoginUser.ROLE_ADMIN, null));
        UserSaveRequest enable = new UserSaveRequest();
        enable.setStatus(1);
        userService.update(id, enable);
        LoginUser.clear();
        assertTrue(authInterceptor.preHandle(requestWith(token), new MockHttpServletResponse(), loginOnlyHandler()));

        // 4) 逻辑删除 -> 旧 token 立即失效
        LoginUser.clear();
        LoginUser.set(new LoginUser(-1L, "it_admin", "集成测试管理员", LoginUser.ROLE_ADMIN, null));
        userService.delete(id);
        LoginUser.clear();
        BusinessException deleted = assertThrows(BusinessException.class,
                () -> authInterceptor.preHandle(requestWith(token), new MockHttpServletResponse(), loginOnlyHandler()));
        assertEquals(Result.CODE_UNAUTHORIZED, deleted.getCode());
        assertTrue(deleted.getMessage().contains("删除"));
    }

    @Test
    @DisplayName("token 里没有角色：学生 token 过管理员接口仍 403（角色来自数据库）")
    void roleComesFromDatabaseInRealEnvironment() {
        Long id = userService.create(saveRequest(RUN + "_s11", "student", insertClass(), RAW_PASSWORD));
        LoginUser.clear();

        BusinessException e = assertThrows(BusinessException.class,
                () -> authInterceptor.preHandle(requestWith(jwtUtil.generate(id)),
                        new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
    }

    // ==================== 测试数据工厂 ====================

    /** 班级模块还没合入，测试里直接 insert 一行真班级（事务回滚后自动消失） */
    private Long insertClass() {
        String className = RUN + "_班级";
        jdbcTemplate.update("INSERT INTO `class` (class_name) VALUES (?)", className);
        return jdbcTemplate.queryForObject("SELECT id FROM `class` WHERE class_name = ?", Long.class, className);
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static UserSaveRequest saveRequest(String username, String role, Long classId, String password) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setRealName("集成测试用户");
        request.setRole(role);
        request.setClassId(classId);
        return request;
    }

    private static MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/page");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    /** 仅登录即可访问的接口 */
    static class InfoController {
        public void info() {
        }
    }

    /** 仅管理员可访问的接口 */
    static class AdminController {
        @RequireRole({"admin"})
        public void create() {
        }
    }

    private static HandlerMethod loginOnlyHandler() {
        return handler(new InfoController(), "info");
    }

    private static HandlerMethod adminOnlyHandler() {
        return handler(new AdminController(), "create");
    }

    private static HandlerMethod handler(Object bean, String methodName) {
        try {
            Method method = bean.getClass().getMethod(methodName);
            return new HandlerMethod(bean, method);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
