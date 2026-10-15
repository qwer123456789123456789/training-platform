package com.util.demo2.clazz.controller;

import com.util.demo2.auth.RequireRole;
import com.util.demo2.clazz.dto.ClassSaveRequest;
import com.util.demo2.clazz.service.ClassService;
import com.util.demo2.clazz.vo.ClassVO;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
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
 * 班级模块接口，路径与《接口文档》完全一致：
 *   GET    /api/class/page     分页查询
 *   GET    /api/class/{id}     查询单个班级
 *   POST   /api/class          新增班级
 *   PUT    /api/class/{id}     修改班级
 *   DELETE /api/class/{id}     逻辑删除
 *
 * 权限：查询只需登录；新增/修改/删除限 admin 和 teacher（需求：教师创建班级、管理员管理班级信息）。
 */
@RestController
@RequestMapping("/api/class")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping("/page")
    public Result<PageResult<ClassVO>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String className) {
        return Result.success(classService.page(current, size, className));
    }

    @GetMapping("/{id}")
    public Result<ClassVO> getById(@PathVariable Long id) {
        return Result.success(classService.getById(id));
    }

    @PostMapping
    @RequireRole({"admin", "teacher"})
    public Result<Long> create(@RequestBody ClassSaveRequest request) {
        return Result.success("新增成功", classService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole({"admin", "teacher"})
    public Result<Void> update(@PathVariable Long id, @RequestBody ClassSaveRequest request) {
        classService.update(id, request);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"admin", "teacher"})
    public Result<Void> delete(@PathVariable Long id) {
        classService.delete(id);
        return Result.success("删除成功", null);
    }
}
