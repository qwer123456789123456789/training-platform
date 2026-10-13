package com.training.dto;

/**
 * 班级新增/修改请求体。
 * POST /api/class 与 PUT /api/class/{id} 共用。
 */
public class ClassDTO {

    private String className;
    private Long teacherId;

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
}
