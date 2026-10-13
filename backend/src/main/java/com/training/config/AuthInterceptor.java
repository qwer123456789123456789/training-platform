package com.training.config;

import com.training.common.BizException;
import com.training.common.UserContext;
import com.training.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录态校验拦截器（占位实现，需与后端1统一）。
 *
 * 约定：请求头携带 Authorization: Bearer {token}，token 为 JWT，subject = userId。
 * 后端1 生成 token 保持同样格式即可无缝对接；若后端1 更换方案，只需改本类，
 * 其余业务代码只依赖 UserContext，不受影响。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
            throw new BizException(401, "未登录或token失效");
        }
        String token = auth.substring(7);
        try {
            Long userId = jwtUtil.getUserId(token);
            Object role = jwtUtil.parseToken(token).get("role");
            UserContext.set(userId, role == null ? null : String.valueOf(role));
            return true;
        } catch (Exception e) {
            throw new BizException(401, "未登录或token失效");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
