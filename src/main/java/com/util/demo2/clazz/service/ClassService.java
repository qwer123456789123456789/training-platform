package com.util.demo2.clazz.service;

import com.util.demo2.clazz.dto.ClassSaveRequest;
import com.util.demo2.clazz.entity.ClassInfo;
import com.util.demo2.clazz.repository.ClassRepository;
import com.util.demo2.clazz.vo.ClassVO;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班级模块业务逻辑：分页查询、详情、新增、修改、逻辑删除。
 */
@Service
public class ClassService {

    private static final long PAGE_SIZE_MAX = 100;

    private final ClassRepository classRepository;

    public ClassService(ClassRepository classRepository) {
        this.classRepository = classRepository;
    }

    /** 分页查询，className 为模糊匹配 */
    public PageResult<ClassVO> page(long current, long size, String className) {
        long safeCurrent = current < 1 ? 1 : current;
        long safeSize = size < 1 ? 10 : Math.min(size, PAGE_SIZE_MAX);
        String safeName = trimToNull(className);
        long total = classRepository.count(safeName);
        List<ClassVO> records = total == 0
                ? List.of()
                : classRepository.page(safeName, (safeCurrent - 1) * safeSize, safeSize);
        return PageResult.of(records, total, safeCurrent, safeSize);
    }

    public ClassVO getById(Long id) {
        return ClassVO.from(classRepository.findById(id)
                .orElseThrow(() -> new BusinessException("班级不存在或已被删除")));
    }

    /** 新增班级，返回新班级 id */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ClassSaveRequest request) {
        String className = trimToNull(request.getClassName());
        if (className == null) {
            throw new BusinessException("班级名称不能为空");
        }
        if (className.length() > 50) {
            throw new BusinessException("班级名称长度不能超过 50");
        }
        Long teacherId = request.getTeacherId();
        if (teacherId != null && !classRepository.existsTeacher(teacherId)) {
            throw new BusinessException("负责教师不存在：teacherId = " + teacherId);
        }
        ClassInfo c = new ClassInfo();
        c.setClassName(className);
        c.setTeacherId(teacherId);
        return classRepository.insert(c);
    }

    /** 修改班级：只更新请求里传了的字段，传了 teacherId 必须真实存在 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ClassSaveRequest request) {
        ClassInfo exist = classRepository.findById(id)
                .orElseThrow(() -> new BusinessException("班级不存在或已被删除"));
        String className = trimToNull(request.getClassName());
        if (className != null && className.length() > 50) {
            throw new BusinessException("班级名称长度不能超过 50");
        }
        Long teacherId = request.getTeacherId();
        if (teacherId != null && !classRepository.existsTeacher(teacherId)) {
            throw new BusinessException("负责教师不存在：teacherId = " + teacherId);
        }
        if (className != null) {
            exist.setClassName(className);
        }
        if (teacherId != null) {
            exist.setTeacherId(teacherId);
        }
        classRepository.update(exist);
    }

    /** 逻辑删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        classRepository.findById(id)
                .orElseThrow(() -> new BusinessException("班级不存在或已被删除"));
        classRepository.logicDelete(id);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
