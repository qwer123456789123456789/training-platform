package com.training.controller;

import com.training.common.BizException;
import com.training.common.PageResult;
import com.training.common.Result;
import com.training.common.UserContext;
import com.training.dto.TaskDTO;
import com.training.entity.TrainingTask;
import com.training.service.TrainingTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实训任务模块接口。
 */
@RestController
@RequestMapping("/api/training-task")
public class TrainingTaskController {

    @Autowired
    private TrainingTaskService trainingTaskService;

    /** 分页查询：GET /api/training-task/page?current=1&size=10&classId=1&taskName=xx */
    @GetMapping("/page")
    public Result<PageResult<TrainingTask>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long classId,
                                                 @RequestParam(required = false) String taskName) {
        return Result.ok(trainingTaskService.page(current, size, classId, taskName));
    }

    /** 查询单个：GET /api/training-task/{id} */
    @GetMapping("/{id}")
    public Result<TrainingTask> getById(@PathVariable Long id) {
        return Result.ok(trainingTaskService.getById(id));
    }

    /** 发布任务：POST /api/training-task，createUserId 取自登录 token */
    @PostMapping
    public Result<Void> create(@RequestBody TaskDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        trainingTaskService.create(dto, userId);
        return Result.ok();
    }

    /** 修改任务：PUT /api/training-task/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskDTO dto) {
        trainingTaskService.update(id, dto);
        return Result.ok();
    }

    /** 删除任务（逻辑删除）：DELETE /api/training-task/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingTaskService.delete(id);
        return Result.ok();
    }
}
