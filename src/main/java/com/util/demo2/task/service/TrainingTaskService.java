package com.util.demo2.task.service;

import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import com.util.demo2.task.dto.TaskSaveRequest;
import com.util.demo2.task.entity.TrainingTask;
import com.util.demo2.task.repository.TrainingTaskRepository;
import com.util.demo2.task.vo.TaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * 实训任务模块业务逻辑：分页查询、详情、发布、修改、逻辑删除。
 */
@Service
public class TrainingTaskService {

    private static final long PAGE_SIZE_MAX = 100;
    private static final Set<String> STATUSES = Set.of("not_start", "ongoing", "finished");
    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TrainingTaskRepository trainingTaskRepository;

    public TrainingTaskService(TrainingTaskRepository trainingTaskRepository) {
        this.trainingTaskRepository = trainingTaskRepository;
    }

    /** 分页查询：可按班级、任务名过滤 */
    public PageResult<TaskVO> page(long current, long size, Long classId, String taskName) {
        long safeCurrent = current < 1 ? 1 : current;
        long safeSize = size < 1 ? 10 : Math.min(size, PAGE_SIZE_MAX);
        String safeName = trimToNull(taskName);
        long total = trainingTaskRepository.count(classId, safeName);
        List<TaskVO> records = total == 0
                ? List.of()
                : trainingTaskRepository.page(classId, safeName, (safeCurrent - 1) * safeSize, safeSize);
        return PageResult.of(records, total, safeCurrent, safeSize);
    }

    public TaskVO getById(Long id) {
        return TaskVO.from(trainingTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在或已被删除")));
    }

    /** 发布任务：createUserId 为当前登录教师/管理员 id，返回新任务 id */
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaskSaveRequest request, Long createUserId) {
        TrainingTask task = new TrainingTask();
        applyRequest(task, request, true);
        task.setCreateUserId(createUserId);
        return trainingTaskRepository.insert(task);
    }

    /** 修改任务：只更新请求里传了的字段 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TaskSaveRequest request) {
        TrainingTask exist = trainingTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在或已被删除"));
        applyRequest(exist, request, false);
        trainingTaskRepository.update(exist);
    }

    /** 逻辑删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        trainingTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在或已被删除"));
        trainingTaskRepository.logicDelete(id);
    }

    /** 校验并回填请求字段；creating=true 时强制必填，否则只改传了的字段 */
    private void applyRequest(TrainingTask task, TaskSaveRequest request, boolean creating) {
        String taskName = trimToNull(request.getTaskName());
        if (creating && taskName == null) {
            throw new BusinessException("任务名称不能为空");
        }
        if (taskName != null && taskName.length() > 100) {
            throw new BusinessException("任务名称长度不能超过 100");
        }

        Long classId = request.getClassId();
        if (creating && classId == null) {
            throw new BusinessException("所属班级不能为空");
        }
        if (classId != null && !trainingTaskRepository.existsClass(classId)) {
            throw new BusinessException("班级不存在：classId = " + classId);
        }

        String status = trimToNull(request.getStatus());
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException("状态只能是 not_start / ongoing / finished");
        }

        if (request.getTotalScore() != null && request.getTotalScore() < 0) {
            throw new BusinessException("总分不能为负数");
        }

        LocalDateTime deadline = parseDeadline(request.getDeadline());

        if (taskName != null) {
            task.setTaskName(taskName);
        }
        if (request.getTaskDesc() != null) {
            task.setTaskDesc(request.getTaskDesc());
        }
        if (classId != null) {
            task.setClassId(classId);
        }
        if (request.getTotalScore() != null) {
            task.setTotalScore(request.getTotalScore());
        }
        if (deadline != null) {
            task.setDeadline(deadline);
        }
        if (status != null) {
            task.setStatus(status);
        }
    }

    private LocalDateTime parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(deadline.trim(), DEADLINE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("截止时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
