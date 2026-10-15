package com.util.demo2.clazz.entity;

import java.time.LocalDateTime;

/**
 * 班级表 class 的实体，字段与建库脚本一一对应。
 * 用 ClassInfo 命名以避开 java.lang.Class 冲突；表名在 SQL 里用反引号 `class` 包裹。
 */
public class ClassInfo {

    private Long id;
    private String className;
    /** 负责教师 id，对应 user.id，允许为空 */
    private Long teacherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
