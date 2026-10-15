package com.util.demo2.auth;

/**
 * 当前登录用户，放在 ThreadLocal 里，一次请求内任意位置都能取到。
 *
 * 用法：UserContext 里只在拦截器 preHandle 中 set，在 afterCompletion 中 clear，
 * 否则线程池复用线程会造成"上一个用户的身份泄漏给下一个请求"。
 */
public class LoginUser {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_ADMIN = "admin";

    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long classId;

    public LoginUser() {
    }

    public LoginUser(Long id, String username, String realName, String role, Long classId) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.classId = classId;
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 未登录时返回 null */
    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户 id，未登录返回 null */
    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getId();
    }

    /** 当前登录用户角色，未登录返回 null */
    public static String currentRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isTeacher() {
        return ROLE_TEACHER.equals(role);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }
}
