package com.example.submissionpoint.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Submission {
    private Integer id;
    private Integer taskId;
    private Integer studentId;
    private String fileName;
    private String filePath;
    private LocalDateTime submitTime;
    private Integer score;
    private String comment;
    private Integer isLocked;
}