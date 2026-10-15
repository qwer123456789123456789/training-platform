package com.util.demo2.auth;

import com.util.demo2.common.BusinessException;
import com.util.demo2.common.Result;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 登录校验 + 权限校验拦截器，覆盖 /api/**（登录接口在 WebConfig 里放行）。
 *
 * 流程：解析 token → 查库确认账号仍有效 → 写入 ThreadLocal → 校验 @RequireRole
 * 401 = 没登录 / token 无效过期 / 账号被删除或禁用
 * 403 = 已登录但角色不允许访问
 *
 * 这里抛出的 BusinessException 会被 GlobalExceptionHandler 捕获（Spring MVC 会处理
 * preHandle 抛出的异常），所以不需要手动往 response 里写 JSON。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public AuthInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求、静态资源等不是 HandlerMethod，直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Long userId = jwtUtil.parseUserId(resolveToken(request));

        // 每次请求都回查数据库：账号被禁用、被逻辑删除后，旧 token 立即失效
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "账号不存在或已被删除，请重新登录");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getClassId());
        LoginUser.set(loginUser);

        checkRole(handlerMethod, loginUser);
        return true;
    }

    /** 必须清理 ThreadLocal，否则线程池复用会造成身份串号 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                               Exception ex) {
        LoginUser.clear();
    }

    private void checkRole(HandlerMethod handlerMethod, LoginUser loginUser) {
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return;
        }
        List<String> allowed = Arrays.asList(requireRole.value());
        if (!allowed.contains(loginUser.getRole())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "权限不足，无法访问该接口");
        }
    }

    /**
     * 兼容两份文档的写法（这一点需要组内统一，见 README）：
     * 统一接口规范写的是 token: xxx，接口文档写的是 Authorization: Bearer {token}，这里两种都支持。
     */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            token = request.getHeader("token");
        }
        if (token == null) {
            return null;
        }
        token = token.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token;
    }
}
