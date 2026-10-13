package com.training.service;

import com.training.common.PageResult;
import com.training.dto.TaskDTO;
import com.training.entity.TrainingTask;

/**
 * 实训任务模块服务。
 */
public interface TrainingTaskService {

    /** 分页查询任务 */
    PageResult<TrainingTask> page(long current, long size, Long classId, String taskName);

    /** 查询单个任务 */
    TrainingTask getById(Long id);

    /** 发布任务，createUserId 为当前登录教师 id */
    void create(TaskDTO dto, Long createUserId);

    /** 修改任务 */
    void update(Long id, TaskDTO dto);

    /** 删除任务（逻辑删除） */
    void delete(Long id);
}
