package com.util.demo2.user.vo;

import com.util.demo2.user.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的用户视图对象：故意不包含 password 字段，从源头上杜绝密码泄露。
 *
 * 时间统一用 String 输出（yyyy-MM-dd HH:mm:ss），既符合《统一接口规范》，
 * 也避免 Jackson 3 的日期序列化配置差异。
 */
public class UserVO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long classId;
    /** 班级名称，来自 class 表左连接，属于本模块给前端的便利字段 */
    private String className;
    private Integer status;
    private String createTime;
    private String updateTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setClassId(user.getClassId());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(format(user.getCreateTime()));
        vo.setUpdateTime(format(user.getUpdateTime()));
        return vo;
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
