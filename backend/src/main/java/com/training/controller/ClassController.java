package com.training.controller;

import com.training.common.PageResult;
import com.training.common.Result;
import com.training.dto.ClassDTO;
import com.training.entity.ClassInfo;
import com.training.service.ClassService;
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
 * 班级模块接口。
 */
@RestController
@RequestMapping("/api/class")
public class ClassController {

    @Autowired
    private ClassService classService;

    /** 分页查询：GET /api/class/page?current=1&size=10&className=xx */
    @GetMapping("/page")
    public Result<PageResult<ClassInfo>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String className) {
        return Result.ok(classService.page(current, size, className));
    }

    /** 查询单个：GET /api/class/{id} */
    @GetMapping("/{id}")
    public Result<ClassInfo> getById(@PathVariable Long id) {
        return Result.ok(classService.getById(id));
    }

    /** 新增：POST /api/class */
    @PostMapping
    public Result<Void> create(@RequestBody ClassDTO dto) {
        classService.create(dto);
        return Result.ok();
    }

    /** 修改：PUT /api/class/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ClassDTO dto) {
        classService.update(id, dto);
        return Result.ok();
    }

    /** 删除（逻辑删除）：DELETE /api/class/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        classService.delete(id);
        return Result.ok();
    }
}
