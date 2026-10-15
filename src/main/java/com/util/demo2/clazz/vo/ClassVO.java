package com.util.demo2.clazz.vo;

import com.util.demo2.clazz.entity.ClassInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的班级视图对象。
 * 时间统一用 String 输出（yyyy-MM-dd HH:mm:ss），与后端 1 的 UserVO 约定一致，
 * 避免 Jackson 3 的日期序列化配置差异。
 * teacherName 只在分页查询的 LEFT JOIN 里有值，单查（from 实体）时该字段为 null。
 */
public class ClassVO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String className;
    private Long teacherId;
    /** 负责教师姓名，来自 user 表左连接，便利字段 */
    private String teacherName;
    private String createTime;
    private String updateTime;

    public static ClassVO from(ClassInfo c) {
        ClassVO vo = new ClassVO();
        vo.setId(c.getId());
        vo.setClassName(c.getClassName());
        vo.setTeacherId(c.getTeacherId());
        vo.setCreateTime(format(c.getCreateTime()));
        vo.setUpdateTime(format(c.getUpdateTime()));
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

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
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
