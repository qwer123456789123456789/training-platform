package com.training.dto;

import java.time.LocalDateTime;

/**
 * 实训任务发布/修改请求体。
 * POST /api/training-task 与 PUT /api/training-task/{id} 共用。
 * createUserId 不从请求体传，由登录 token 自动获取。
 */
public class TaskDTO {

    private String taskName;
    private String taskDesc;
    private Long classId;
    private Integer totalScore;
    private LocalDateTime deadline;
    /** 可选：not_start/ongoing/finished，不传默认 not_start */
    private String status;

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

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
