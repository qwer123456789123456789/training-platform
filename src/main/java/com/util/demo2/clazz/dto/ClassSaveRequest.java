package com.util.demo2.clazz.dto;

/**
 * 班级新增/修改请求体，两种场景复用：
 * 新增：className 必填；teacherId 可选（传了就必须是真实存在的教师）；
 * 修改：className / teacherId 均可选，传了才改，不传保持原值。
 *
 * 校验逻辑写在 ClassService 里（与后端 1 的 UserSaveRequest 同一套约定）。
 */
public class ClassSaveRequest {

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
