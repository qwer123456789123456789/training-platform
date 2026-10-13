package com.util.demo2.user.dto;

/**
 * 新增/修改用户请求体，两种场景复用：
 *
 * 新增：username、password、realName、role 必填，classId/status 可选（status 默认 1）
 * 修改：password 留空表示不改密码；username 不允许修改（传了也会被忽略）
 *
 * 校验逻辑写在 UserService 里，因为新增和修改的必填项不一样，
 * 用注解无法区分场景（注解只能一刀切）。
 */
public class UserSaveRequest {

    private String username;
    private String password;
    private String realName;
    /** student / teacher / admin */
    private String role;
    private Long classId;
    /** 0 禁用 1 启用 */
    private Integer status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
