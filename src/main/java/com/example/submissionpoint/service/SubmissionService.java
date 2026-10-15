package com.example.submissionpoint.service;

import com.example.submissionpoint.entity.Submission;
import java.util.List;

public interface SubmissionService {
    int add(Submission submission);
    Submission getById(Integer id);
    List<Submission> list();
    List<Submission> getByTaskStudent(Integer taskId, Integer studentId);
    int update(Submission submission);
}