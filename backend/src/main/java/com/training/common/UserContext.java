package com.training.common;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由登录态拦截器在请求进入时写入，请求结束后清除。
 * 与后端1的鉴权拦截器共用：后端1解析 token 后调用 UserContext.set(userId, role) 即可。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
