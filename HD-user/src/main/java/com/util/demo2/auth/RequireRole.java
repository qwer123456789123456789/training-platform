package com.util.demo2.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色权限注解，由 AuthInterceptor 解析。
 *
 * 用法：
 *   @RequireRole({"admin"})                 只有管理员可访问
 *   @RequireRole({"admin", "teacher"})      管理员和教师可访问
 * 不写注解 = 只要登录就能访问。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    String[] value();
}
