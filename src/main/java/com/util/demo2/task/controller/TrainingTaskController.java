package com.util.demo2.task.controller;

import com.util.demo2.auth.LoginUser;
import com.util.demo2.auth.RequireRole;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
import com.util.demo2.task.dto.TaskSaveRequest;
import com.util.demo2.task.service.TrainingTaskService;
import com.util.demo2.task.vo.TaskVO;
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
 * 实训任务模块接口，路径与《接口文档》完全一致：
 *   GET    /api/training-task/page    分页查询
 *   GET    /api/training-task/{id}    查询单个任务
 *   POST   /api/training-task         发布任务
 *   PUT    /api/training-task/{id}    修改任务
 *   DELETE /api/training-task/{id}    逻辑删除
 *
 * 权限：查询只需登录（学生要看任务）；发布/修改/删除限 admin 和 teacher。
 * createUserId 从登录 token（LoginUser）自动获取，请求体不用传。
 */
@RestController
@RequestMapping("/api/training-task")
public class TrainingTaskController {

    private final TrainingTaskService trainingTaskService;

    public TrainingTaskController(TrainingTaskService trainingTaskService) {
        this.trainingTaskService = trainingTaskService;
    }

    @GetMapping("/page")
    public Result<PageResult<TaskVO>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) Long classId,
                                           @RequestParam(required = false) String taskName) {
        return Result.success(trainingTaskService.page(current, size, classId, taskName));
    }

    @GetMapping("/{id}")
    public Result<TaskVO> getById(@PathVariable Long id) {
        return Result.success(trainingTaskService.getById(id));
    }

    @PostMapping
    @RequireRole({"admin", "teacher"})
    public Result<Long> create(@RequestBody TaskSaveRequest request) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "未登录");
        }
        return Result.success("发布成功", trainingTaskService.create(request, userId));
    }

    @PutMapping("/{id}")
    @RequireRole({"admin", "teacher"})
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskSaveRequest request) {
        trainingTaskService.update(id, request);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"admin", "teacher"})
    public Result<Void> delete(@PathVariable Long id) {
        trainingTaskService.delete(id);
        return Result.success("删除成功", null);
    }
}
