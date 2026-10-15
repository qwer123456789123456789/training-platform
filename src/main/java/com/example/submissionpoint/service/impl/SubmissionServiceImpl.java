package com.example.submissionpoint.service.impl;

import com.example.submissionpoint.entity.Submission;
import com.example.submissionpoint.mapper.SubmissionMapper;
import com.example.submissionpoint.service.SubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    @Autowired
    private SubmissionMapper submissionMapper;

    @Override
    public int add(Submission submission) {
        if(submission.getSubmitTime() == null){
            submission.setSubmitTime(LocalDateTime.now());
        }
        return submissionMapper.insert(submission);
    }

    @Override
    public Submission getById(Integer id) {
        return submissionMapper.selectById(id);
    }

    @Override
    public List<Submission> list() {
        return submissionMapper.selectAll();
    }

    @Override
    public List<Submission> getByTaskStudent(Integer taskId, Integer studentId) {
        return submissionMapper.selectByTaskAndStudent(taskId, studentId);
    }

    @Override
    public int update(Submission submission) {
        return submissionMapper.update(submission);
    }
}