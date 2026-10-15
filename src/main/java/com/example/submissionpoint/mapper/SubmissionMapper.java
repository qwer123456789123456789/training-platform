
package com.example.submissionpoint.mapper;

import com.example.submissionpoint.entity.Submission;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface SubmissionMapper {
    int insert(Submission submission);
    Submission selectById(Integer id);
    List<Submission> selectAll();
    List<Submission> selectByTaskAndStudent(@Param("taskId") Integer taskId, @Param("studentId") Integer studentId);
    int update(Submission submission);
}