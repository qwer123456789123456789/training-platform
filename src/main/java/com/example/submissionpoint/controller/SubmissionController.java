package com.example.submissionpoint.controller;

import com.example.submissionpoint.entity.Submission;
import com.example.submissionpoint.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/submission")
@Tag(name = "提交记录接口")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    @PostMapping
    @Operation(summary = "新增提交记录")
    public Map<String,Object> add(@RequestBody Submission submission){
        Map<String,Object> res = new HashMap<>();
        int count = submissionService.add(submission);
        if(count > 0){
            res.put("code",200);
            res.put("msg","成功");
        }else{
            res.put("code",500);
            res.put("msg","失败");
        }
        return res;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询")
    public Map<String,Object> getOne(@PathVariable Integer id){
        Map<String,Object> res = new HashMap<>();
        res.put("code",200);
        res.put("msg","成功");
        res.put("data",submissionService.getById(id));
        return res;
    }

    @GetMapping
    @Operation(summary = "查询全部提交记录")
    public Map<String,Object> list(){
        Map<String,Object> res = new HashMap<>();
        res.put("code",200);
        res.put("msg","成功");
        res.put("data",submissionService.list());
        return res;
    }

    @GetMapping("/task-student")
    @Operation(summary = "根据任务id和学生id查询")
    public Map<String,Object> getByTaskStudent(Integer taskId,Integer studentId){
        Map<String,Object> res = new HashMap<>();
        res.put("code",200);
        res.put("msg","成功");
        res.put("data",submissionService.getByTaskStudent(taskId,studentId));
        return res;
    }

    @PutMapping
    @Operation(summary = "修改提交记录")
    public Map<String,Object> update(@RequestBody Submission submission){
        Map<String,Object> res = new HashMap<>();
        int count = submissionService.update(submission);
        if(count > 0){
            res.put("code",200);
            res.put("msg","成功");
        }else{
            res.put("code",500);
            res.put("msg","失败");
        }
        return res;
    }
}