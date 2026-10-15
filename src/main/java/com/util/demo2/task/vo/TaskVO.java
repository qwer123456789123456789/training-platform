package com.util.demo2.task.vo;

import com.util.demo2.task.entity.TrainingTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的实训任务视图对象。
 * 时间统一用 String 输出（yyyy-MM-dd HH:mm:ss），与后端 1 的 UserVO 约定一致。
 * className 只在分页查询的 LEFT JOIN 里有值，单查（from 实体）时该字段为 null。
 */
public class TaskVO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String taskName;
    private String taskDesc;
    private Long classId;
    /** 班级名称，来自 class 表左连接，便利字段 */
    private String className;
    private Integer totalScore;
    private String deadline;
    private String status;
    private Long createUserId;
    private String createTime;
    private String updateTime;

    public static TaskVO from(TrainingTask t) {
        TaskVO vo = new TaskVO();
        vo.setId(t.getId());
        vo.setTaskName(t.getTaskName());
        vo.setTaskDesc(t.getTaskDesc());
        vo.setClassId(t.getClassId());
        vo.setTotalScore(t.getTotalScore());
        vo.setDeadline(format(t.getDeadline()));
        vo.setStatus(t.getStatus());
        vo.setCreateUserId(t.getCreateUserId());
        vo.setCreateTime(format(t.getCreateTime()));
        vo.setUpdateTime(format(t.getUpdateTime()));
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

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDesc() {
        return taskDesc;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
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

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
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
