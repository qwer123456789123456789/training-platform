package com.util.demo2.user.vo;

/**
 * 登录成功返回体。
 * 接口文档原本只有 token/role/realName，这里补上 id 和 classId：
 * 学生提交成果要用 studentId，教师查本班学生要用 classId，前端登录后缓存这两个值就不用再请求一次。
 * （属于接口变更，需要在群里同步，见 README-user-auth.md）
 */
public class LoginVO {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long classId;
    private String token;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
