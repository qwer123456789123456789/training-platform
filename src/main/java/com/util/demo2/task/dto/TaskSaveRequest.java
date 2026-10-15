package com.util.demo2.task.dto;

/**
 * 实训任务发布/修改请求体，两种场景复用：
 * 发布：taskName、classId 必填；taskDesc/totalScore/deadline/status 可选；
 * 修改：全部可选，传了才改，不传保持原值。
 *
 * createUserId 不从请求体传，由登录 token（LoginUser）自动获取。
 * deadline 用字符串 yyyy-MM-dd HH:mm:ss，避免 Jackson 3 日期反序列化配置差异。
 */
public class TaskSaveRequest {

    private String taskName;
    private String taskDesc;
    private Long classId;
    private Integer totalScore;
    /** 截止时间，字符串 yyyy-MM-dd HH:mm:ss */
    private String deadline;
    /** not_start / ongoing / finished，不传默认 not_start */
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
}
