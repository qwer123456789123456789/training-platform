package com.util.demo2.user.controller;

import com.util.demo2.auth.AuthInterceptor;
import com.util.demo2.auth.JwtUtil;
import com.util.demo2.auth.LoginUser;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.GlobalExceptionHandler;
import com.util.demo2.common.PageResult;
import com.util.demo2.user.dto.UserSaveRequest;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import com.util.demo2.user.service.UserService;
import com.util.demo2.user.vo.LoginVO;
import com.util.demo2.user.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户模块接口契约测试（HTTP 层）。
 *
 * 用 MockMvc 把「UserController + @Valid 参数校验 + 全局异常处理 + 登录/权限拦截器」整条链路跑一遍：
 * 不连数据库、不启动 Spring 容器，但接口路径、请求头、返回体结构和线上完全一致。
 * 钉死前端最依赖的几条约定：
 *   - 返回体恒为 {code,msg,data}，HTTP 状态码恒为 200，业务码看 body；
 *   - 没登录 → 401；已登录但角色不够 → 403；参数不合法 → 400；
 *   - 分页 data 只有 records/total/current/size；
 *   - 修改用户时前端传 password:""（编辑表单没改密码）必须放行。
 *
 * 运行：mvnw.cmd test -Dtest=UserApiContractTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserApiContractTest {

    private static final String SECRET = "unit-test-jwt-secret-key-0123456789abcdef";

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 120);
        adminToken = jwtUtil.generate(1L);
        studentToken = jwtUtil.generate(2L);

        AuthInterceptor authInterceptor = new AuthInterceptor(jwtUtil, userRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // 与 WebConfig 保持一致：/api/** 都要登录，只有登录接口在白名单里
                .addInterceptors(new MappedInterceptor(new String[]{"/api/**"},
                        new String[]{"/api/user/login"}, authInterceptor))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser(1L, "admin", LoginUser.ROLE_ADMIN, null)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeUser(2L, "s001", LoginUser.ROLE_STUDENT, 1L)));
    }

    @AfterEach
    void tearDown() {
        LoginUser.clear();
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：{code:200,msg:登录成功,data:{token,id,role,classId}}，不返回密码")
    void loginReturnsToken() throws Exception {
        LoginVO vo = new LoginVO();
        vo.setId(2L);
        vo.setUsername("s001");
        vo.setRealName("张三");
        vo.setRole(LoginUser.ROLE_STUDENT);
        vo.setClassId(1L);
        vo.setToken("aaa.bbb.ccc");
        when(userService.login(any())).thenReturn(vo);

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s001\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("登录成功"))
                .andExpect(jsonPath("$.data.token").value("aaa.bbb.ccc"))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(jsonPath("$.data.classId").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("登录不传密码：400 且提示明确（HTTP 状态码仍是 200）")
    void loginWithoutPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("密码不能为空"));
    }

    @Test
    @DisplayName("登录接口在拦截器白名单里：不带 token 也能访问")
    void loginIsWhitelisted() throws Exception {
        when(userService.login(any())).thenReturn(new LoginVO());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("登录请求体不是合法 JSON：400（不是 500「服务器异常」）")
    void loginWithBrokenJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求体格式错误，请检查是否为合法 JSON"));
    }

    // ==================== 权限矩阵 ====================

    @Test
    @DisplayName("分页接口不带 token：401（HTTP 仍是 200，前端判 body 的 code）")
    void pageWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/user/page").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未登录，请先登录"));
    }

    @Test
    @DisplayName("学生调分页接口：403")
    void pageAsStudentReturns403() throws Exception {
        mockMvc.perform(get("/api/user/page").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理员调分页接口：200，data 固定 records/total/current/size")
    void pageAsAdminReturnsUnifiedShape() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(2L);
        vo.setUsername("s001");
        vo.setRole("student");
        vo.setClassName("2025计算机1班");
        when(userService.page(anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(PageResult.of(List.of(vo), 1, 1, 10));

        mockMvc.perform(get("/api/user/page")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("current", "1")
                        .param("size", "10")
                        .param("role", "student")
                        .param("status", "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records[0].username").value("s001"))
                .andExpect(jsonPath("$.data.records[0].className").value("2025计算机1班"));
    }

    @Test
    @DisplayName("分页参数类型写错（classId=abc）：400 并带上参数名")
    void pageWithBadParamTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/user/page")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("classId", "abc"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("参数类型错误：classId"));
    }

    @Test
    @DisplayName("学生新增用户：403，且根本不会走到 Service")
    void createAsStudentReturns403() throws Exception {
        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s009\",\"password\":\"123456\",\"realName\":\"新同学\",\"role\":\"student\",\"classId\":1}"))
                .andExpect(jsonPath("$.code").value(403));

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("学生删除用户：403")
    void deleteAsStudentReturns403() throws Exception {
        mockMvc.perform(delete("/api/user/1").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.code").value(403));

        verify(userService, never()).delete(any());
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("管理员新增用户：200，data 是新用户 id")
    void createAsAdminReturnsNewId() throws Exception {
        when(userService.create(any(UserSaveRequest.class))).thenReturn(10L);

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s009\",\"password\":\"123456\",\"realName\":\"新同学\",\"role\":\"student\",\"classId\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("新增成功"))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    @DisplayName("新增用户班级不存在：400，提示原样透传给前端")
    void createWithMissingClassReturns400() throws Exception {
        doThrow(new BusinessException("班级不存在：classId = 99"))
                .when(userService).create(any(UserSaveRequest.class));

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s009\",\"password\":\"123456\",\"realName\":\"新同学\",\"role\":\"student\",\"classId\":99}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("班级不存在：classId = 99"));
    }

    // ==================== 修改 ====================

    @Test
    @DisplayName("学生改自己的姓名（密码传空串）：200 —— 编辑表单不改密码必须能保存")
    void updateSelfWithBlankPasswordIsAccepted() throws Exception {
        mockMvc.perform(put("/api/user/2")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"s001\",\"realName\":\"张三丰\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).update(eq(2L), any(UserSaveRequest.class));
    }

    @Test
    @DisplayName("修改时新密码过短：400 由 Service 抛出，提示可读")
    void updateWithShortPasswordReturns400() throws Exception {
        doThrow(new BusinessException("密码不能少于 6 位"))
                .when(userService).update(eq(2L), any(UserSaveRequest.class));

        mockMvc.perform(put("/api/user/2")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("密码不能少于 6 位"));
    }

    @Test
    @DisplayName("未登录改用户：401")
    void updateWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/user/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"张三丰\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 查询单个 ====================

    @Test
    @DisplayName("查自己：200；查别人：403（Service 里判，不打库也能测）")
    void getByIdPermission() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(2L);
        vo.setUsername("s001");
        when(userService.getById(2L)).thenReturn(vo);
        doThrow(new BusinessException(403, "只能查看自己的信息")).when(userService).getById(3L);

        mockMvc.perform(get("/api/user/2").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("s001"));

        mockMvc.perform(get("/api/user/3").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 测试数据工厂 ====================

    private static User activeUser(Long id, String username, String role, Long classId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$not-used-in-this-test");
        user.setRealName(LoginUser.ROLE_ADMIN.equals(role) ? "系统管理员" : "张三");
        user.setRole(role);
        user.setStatus(1);
        user.setClassId(classId);
        return user;
    }
}
