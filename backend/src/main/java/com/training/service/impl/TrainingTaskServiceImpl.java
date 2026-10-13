package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.BizException;
import com.training.common.PageResult;
import com.training.dto.TaskDTO;
import com.training.entity.TrainingTask;
import com.training.mapper.TrainingTaskMapper;
import com.training.service.TrainingTaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 实训任务模块服务实现。
 */
@Service
public class TrainingTaskServiceImpl extends ServiceImpl<TrainingTaskMapper, TrainingTask> implements TrainingTaskService {

    @Override
    public PageResult<TrainingTask> page(long current, long size, Long classId, String taskName) {
        LambdaQueryWrapper<TrainingTask> qw = new LambdaQueryWrapper<>();
        qw.eq(classId != null, TrainingTask::getClassId, classId);
        qw.like(StringUtils.hasText(taskName), TrainingTask::getTaskName, taskName);
        qw.orderByDesc(TrainingTask::getCreateTime);
        Page<TrainingTask> page = this.page(new Page<>(current, size), qw);
        return PageResult.of(page);
    }

    @Override
    public TrainingTask getById(Long id) {
        TrainingTask task = super.getById(id);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        return task;
    }

    @Override
    public void create(TaskDTO dto, Long createUserId) {
        validate(dto);
        TrainingTask task = new TrainingTask();
        task.setTaskName(dto.getTaskName());
        task.setTaskDesc(dto.getTaskDesc());
        task.setClassId(dto.getClassId());
        task.setTotalScore(dto.getTotalScore());
        task.setDeadline(dto.getDeadline());
        if (StringUtils.hasText(dto.getStatus())) {
            task.setStatus(dto.getStatus());
        }
        task.setCreateUserId(createUserId);
        this.save(task);
    }

    @Override
    public void update(Long id, TaskDTO dto) {
        TrainingTask task = getById(id);
        if (StringUtils.hasText(dto.getTaskName())) {
            task.setTaskName(dto.getTaskName());
        }
        task.setTaskDesc(dto.getTaskDesc());
        task.setClassId(dto.getClassId());
        task.setTotalScore(dto.getTotalScore());
        task.setDeadline(dto.getDeadline());
        if (StringUtils.hasText(dto.getStatus())) {
            task.setStatus(dto.getStatus());
        }
        this.updateById(task);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        this.removeById(id);
    }

    private void validate(TaskDTO dto) {
        if (!StringUtils.hasText(dto.getTaskName())) {
            throw new BizException(400, "任务名称不能为空");
        }
        if (dto.getClassId() == null) {
            throw new BizException(400, "所属班级不能为空");
        }
    }
}
