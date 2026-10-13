package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.BizException;
import com.training.common.PageResult;
import com.training.dto.ClassDTO;
import com.training.entity.ClassInfo;
import com.training.mapper.ClassMapper;
import com.training.service.ClassService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 班级模块服务实现。
 */
@Service
public class ClassServiceImpl extends ServiceImpl<ClassMapper, ClassInfo> implements ClassService {

    @Override
    public PageResult<ClassInfo> page(long current, long size, String className) {
        LambdaQueryWrapper<ClassInfo> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(className), ClassInfo::getClassName, className);
        qw.orderByDesc(ClassInfo::getCreateTime);
        Page<ClassInfo> page = this.page(new Page<>(current, size), qw);
        return PageResult.of(page);
    }

    @Override
    public ClassInfo getById(Long id) {
        ClassInfo cls = super.getById(id);
        if (cls == null) {
            throw new BizException(404, "班级不存在");
        }
        return cls;
    }

    @Override
    public void create(ClassDTO dto) {
        if (!StringUtils.hasText(dto.getClassName())) {
            throw new BizException(400, "班级名称不能为空");
        }
        ClassInfo cls = new ClassInfo();
        cls.setClassName(dto.getClassName());
        cls.setTeacherId(dto.getTeacherId());
        this.save(cls);
    }

    @Override
    public void update(Long id, ClassDTO dto) {
        ClassInfo cls = getById(id);
        if (StringUtils.hasText(dto.getClassName())) {
            cls.setClassName(dto.getClassName());
        }
        cls.setTeacherId(dto.getTeacherId());
        this.updateById(cls);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        this.removeById(id);
    }
}
