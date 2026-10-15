package com.util.demo2.user.controller;

import com.util.demo2.auth.RequireRole;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
import com.util.demo2.user.dto.LoginRequest;
import com.util.demo2.user.dto.UserSaveRequest;
import com.util.demo2.user.service.UserService;
import com.util.demo2.user.vo.LoginVO;
import com.util.demo2.user.vo.UserVO;
import jakarta.validation.Valid;
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
 * 用户模块接口，路径与《接口文档》完全一致：
 *   POST   /api/user/login        登录（无需 token）
 *   GET    /api/user/page         分页查询
 *   GET    /api/user/{id}         查询单个用户
 *   POST   /api/user              新增用户
 *   PUT    /api/user/{id}         修改用户
 *   DELETE /api/user/{id}         逻辑删除
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 登录：成功返回 token、角色、姓名（已在文档基础上补充 id 和 classId） */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", userService.login(request));
    }

    /**
     * 分页查询。参数：current、size、username（账号模糊）、role（student/teacher/admin）、
     * classId（按班级筛人）、status（0 禁用 / 1 启用），除 current/size 外都可选。
     * 需求写的是"管理员管理班级、用户"，所以这里先限制 admin；
     * 如果确认教师也要查本班学生，把注解改成 @RequireRole({"admin", "teacher"})，并在 Service 里限制只能查自己班。
     */
    @GetMapping("/page")
    @RequireRole({"admin"})
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) Long classId,
                                           @RequestParam(required = false) Integer status) {
        return Result.success(userService.page(current, size, username, role, classId, status));
    }

    /** 查询单个用户：本人或管理员 */
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    /** 新增用户，返回新用户 id */
    @PostMapping
    @RequireRole({"admin"})
    public Result<Long> create(@RequestBody UserSaveRequest request) {
        return Result.success("新增成功", userService.create(request));
    }

    /** 修改用户：管理员改任何人，其他角色只能改自己（角色/状态/班级不可改） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return Result.success("修改成功", null);
    }

    /** 逻辑删除用户 */
    @DeleteMapping("/{id}")
    @RequireRole({"admin"})
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }
}
