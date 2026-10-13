package com.training.service;

import com.training.common.PageResult;
import com.training.dto.ClassDTO;
import com.training.entity.ClassInfo;

/**
 * 班级模块服务。
 */
public interface ClassService {

    /** 分页查询班级 */
    PageResult<ClassInfo> page(long current, long size, String className);

    /** 根据 id 查询班级 */
    ClassInfo getById(Long id);

    /** 新增班级 */
    void create(ClassDTO dto);

    /** 修改班级 */
    void update(Long id, ClassDTO dto);

    /** 删除班级（逻辑删除） */
    void delete(Long id);
}
