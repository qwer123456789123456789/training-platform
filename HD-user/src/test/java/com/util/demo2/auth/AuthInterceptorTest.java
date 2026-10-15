package com.util.demo2.auth;

import com.util.demo2.common.BusinessException;
import com.util.demo2.common.Result;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 登录校验 + 角色权限校验拦截器单元测试。
 *
 * 这是整个平台的"大门"：所有需要登录的接口都靠它，所以单独测透：
 * 没带 token / token 被篡改 / 过期 → 401；账号被禁用或被删除后旧 token 立即失效；
 * 角色以数据库为准（token 里写什么都不算数）；角色不匹配 → 403；放行后写入 LoginUser；
 * 请求结束必须清理 ThreadLocal（否则线程复用会串号）。
 *
 * 不需要数据库与 Spring 容器：mvnw.cmd test -Dtest=AuthInterceptorTest
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String SECRET = "unit-test-jwt-secret-key-0123456789abcdef";

    @Mock
    private UserRepository userRepository;

    private JwtUtil jwtUtil;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 120);
        interceptor = new AuthInterceptor(jwtUtil, userRepository);
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    // ==================== 401 ====================

    @Test
    @DisplayName("没带 token：401，且提示是「未登录」")
    void missingTokenReturns401() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request(), new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("未登录"));
    }

    @Test
    @DisplayName("token 被篡改（改了 sub 想冒充别人）：401，且不会去查库")
    void tamperedTokenReturns401() throws Exception {
        MockHttpServletRequest request = request();
        String token = jwtUtil.generate(2L);
        String[] parts = token.split("\\.");
        String fakePayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"iat\":1,\"exp\":9999999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer " + parts[0] + "." + fakePayload + "." + parts[2]);

        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("签名"));
    }

    @Test
    @DisplayName("token 已过期：401，提示重新登录")
    void expiredTokenReturns401() {
        JwtUtil expiredJwt = new JwtUtil(SECRET, -1);
        MockHttpServletRequest request = request();
        request.addHeader("token", expiredJwt.generate(1L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("账号已被逻辑删除：token 还没过期也 401（每次请求都回查数据库）")
    void deletedUserReturns401() {
        MockHttpServletRequest request = requestWithBearer(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("删除"));
    }

    @Test
    @DisplayName("账号被禁用：旧 token 立即失效")
    void disabledUserReturns401() {
        MockHttpServletRequest request = requestWithBearer(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "student", 0, 1L)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("禁用"));
    }

    // ==================== 403 ====================

    @Test
    @DisplayName("学生访问仅管理员接口：403（前端要能和 401 区分开）")
    void studentOnAdminApiReturns403() {
        MockHttpServletRequest request = requestWithBearer(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "student", 1, 1L)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(Result.CODE_FORBIDDEN, e.getCode());
        assertTrue(e.getMessage().contains("权限不足"));
    }

    @Test
    @DisplayName("教师访问仅管理员接口：403")
    void teacherOnAdminApiReturns403() {
        MockHttpServletRequest request = requestWithBearer(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "t001", "teacher", 1, null)));

        assertEquals(Result.CODE_FORBIDDEN, assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler())).getCode());
    }

    @Test
    @DisplayName("角色以数据库为准：token 里没有角色，改角色/降权后立刻生效")
    void roleAlwaysComesFromDatabase() {
        // 用户 2 在库里是 student，即便他拿着"自己签发的"token 也过不了管理员接口
        MockHttpServletRequest request = requestWithBearer(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "student", 1, 1L)));

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));
    }

    // ==================== 放行 ====================

    @Test
    @DisplayName("管理员访问管理员接口：放行，并把当前用户写进 LoginUser")
    void adminPassesAndLoginUserFilled() {
        MockHttpServletRequest request = requestWithBearer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "admin", 1, null)));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler()));

        assertEquals(1L, LoginUser.currentUserId().longValue());
        assertEquals("admin", LoginUser.currentRole());
        assertTrue(LoginUser.get().isAdmin());
        assertEquals("系统管理员", LoginUser.get().getRealName());
    }

    @Test
    @DisplayName("接口没写 @RequireRole：只要登录就能访问（学生也可以查自己的信息）")
    void loginOnlyApiPassesForStudent() {
        MockHttpServletRequest request = requestWithBearer(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "s001", "student", 1, 1L)));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), loginOnlyHandler()));
        assertEquals(1L, LoginUser.get().getClassId().longValue());
    }

    @Test
    @DisplayName("两种请求头都支持：Authorization: Bearer xxx 与 token: xxx")
    void bothHeaderStylesAccepted() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "admin", 1, null)));

        MockHttpServletRequest bearer = requestWithBearer(1L);
        assertTrue(interceptor.preHandle(bearer, new MockHttpServletResponse(), adminOnlyHandler()));

        MockHttpServletRequest plain = request();
        plain.addHeader("token", jwtUtil.generate(1L));
        assertTrue(interceptor.preHandle(plain, new MockHttpServletResponse(), adminOnlyHandler()));
    }

    @Test
    @DisplayName("非 HandlerMethod（CORS 预检、静态资源）不拦截")
    void nonHandlerMethodPassesThrough() {
        assertTrue(interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()));
    }

    // ==================== ThreadLocal ====================

    @Test
    @DisplayName("请求结束必须清空 LoginUser，否则线程复用会把 A 的身份带给 B")
    void afterCompletionClearsLoginUser() {
        MockHttpServletRequest request = requestWithBearer(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "admin", 1, null)));

        interceptor.preHandle(request, new MockHttpServletResponse(), adminOnlyHandler());
        assertEquals(1L, LoginUser.currentUserId().longValue());

        interceptor.afterCompletion(request, new MockHttpServletResponse(), adminOnlyHandler(), null);

        assertNull(LoginUser.get());
        assertNull(LoginUser.currentUserId());
        assertNull(LoginUser.currentRole());
    }

    // ==================== 测试脚手架 ====================

    /** 模拟"仅管理员可访问"的接口 */
    static class AdminController {
        @RequireRole({"admin"})
        public void create() {
        }
    }

    /** 模拟"登录即可访问"的接口 */
    static class InfoController {
        public void info() {
        }
    }

    private HandlerMethod adminOnlyHandler() {
        return handler(new AdminController(), "create");
    }

    private HandlerMethod loginOnlyHandler() {
        return handler(new InfoController(), "info");
    }

    private static HandlerMethod handler(Object bean, String methodName) {
        try {
            Method method = bean.getClass().getMethod(methodName);
            return new HandlerMethod(bean, method);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/user/page");
    }

    private MockHttpServletRequest requestWithBearer(Long userId) {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + jwtUtil.generate(userId));
        return request;
    }

    private User user(Long id, String username, String role, int status, Long classId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$not-used-in-this-test");
        user.setRealName(LoginUser.ROLE_ADMIN.equals(role) ? "系统管理员" : "测试用户");
        user.setRole(role);
        user.setStatus(status);
        user.setClassId(classId);
        return user;
    }
}
