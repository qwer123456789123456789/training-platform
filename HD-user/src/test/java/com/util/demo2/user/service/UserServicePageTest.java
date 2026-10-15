package com.util.demo2.user.service;

import com.util.demo2.auth.JwtUtil;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import com.util.demo2.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分页查询规则单元测试：参数夹紧、过滤条件是否真的传到 SQL、空结果形态、实体转 VO。
 * 用 Mockito 假造 UserRepository，不需要数据库。
 *
 * 运行：mvnw.cmd test -Dtest=UserServicePageTest
 */
@ExtendWith(MockitoExtension.class)
class UserServicePageTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, jwtUtil, passwordEncoder);
    }

    @Test
    @DisplayName("正常分页：总条数、页码、每页条数原样返回，偏移量 = (current-1)*size")
    void pagePassesOffsetAndLimit() {
        when(userRepository.count(isNull(), isNull(), isNull(), isNull())).thenReturn(25L);
        when(userRepository.page(isNull(), isNull(), isNull(), isNull(), eq(10L), eq(10L)))
                .thenReturn(List.of(vo(11L, "s011"), vo(12L, "s012")));

        PageResult<UserVO> result = userService.page(2, 10, null, null, null, null);

        assertEquals(25L, result.getTotal().longValue());
        assertEquals(2L, result.getCurrent().longValue());
        assertEquals(10L, result.getSize().longValue());
        assertEquals(2, result.getRecords().size());
        assertEquals("s011", result.getRecords().get(0).getUsername());
    }

    @Test
    @DisplayName("脏参数被夹紧：current=0 → 1，size=999999 → 100（每页上限）")
    void pageParamsAreClamped() {
        when(userRepository.count(isNull(), isNull(), isNull(), isNull())).thenReturn(0L);

        PageResult<UserVO> first = userService.page(0, 999999, null, null, null, null);
        assertEquals(1L, first.getCurrent().longValue());
        assertEquals(100L, first.getSize().longValue());

        PageResult<UserVO> second = userService.page(-5, 0, null, null, null, null);
        assertEquals(1L, second.getCurrent().longValue());
        assertEquals(10L, second.getSize().longValue(), "size < 1 时回落到默认 10 条");
    }

    @Test
    @DisplayName("过滤条件（账号模糊/角色/班级/状态）原样传到 SQL 层")
    void pagePassesFilters() {
        when(userRepository.count("zh", "student", 3L, 1)).thenReturn(1L);
        when(userRepository.page(eq("zh"), eq("student"), eq(3L), eq(1), anyLong(), anyLong()))
                .thenReturn(List.of(vo(2L, "zhangsan")));

        PageResult<UserVO> result = userService.page(1, 10, "zh", "student", 3L, 1);

        assertEquals(1, result.getRecords().size());
        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(userRepository).page(eq("zh"), eq("student"), eq(3L), eq(1), offset.capture(), eq(10L));
        assertEquals(0L, offset.getValue().longValue(), "第 1 页偏移量必须是 0");
    }

    @Test
    @DisplayName("查总数就是 0 时：不再查列表（省一次数据库往返），返回空数组而不是 null")
    void emptyTotalSkipsSecondQuery() {
        when(userRepository.count(any(), any(), any(), any())).thenReturn(0L);

        PageResult<UserVO> result = userService.page(1, 10, "nobody", null, null, null);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal().longValue());
        verify(userRepository, never()).page(any(), any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("非法角色过滤：400（而不是查出一片空白让前端猜）")
    void pageRejectsIllegalRole() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.page(1, 10, null, "superman", null, null));

        assertEquals(Result.CODE_PARAM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("角色"));
        verify(userRepository, never()).count(any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法状态过滤：400")
    void pageRejectsIllegalStatus() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.page(1, 10, null, null, null, 7));

        assertEquals(Result.CODE_PARAM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("状态"));
    }

    @Test
    @DisplayName("密码不返回：UserVO 里没有 password 字段")
    void userVoHasNoPasswordField() {
        assertTrue(Arrays.stream(UserVO.class.getDeclaredFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("password")),
                "UserVO 一旦有 password 字段，分页接口就会把哈希返回给前端");
    }

    @Test
    @DisplayName("实体转 VO：角色/班级名称/时间格式化正确")
    void entityToVoFormatsFields() {
        User user = new User();
        user.setId(2L);
        user.setUsername("s001");
        user.setRealName("张三");
        user.setRole("student");
        user.setClassId(1L);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.of(2026, 10, 13, 10, 20, 31));

        UserVO vo = UserVO.from(user);

        assertEquals("s001", vo.getUsername());
        assertEquals("student", vo.getRole());
        assertEquals("2026-10-13 10:20:31", vo.getCreateTime());
        assertEquals(1L, vo.getClassId().longValue());
    }

    private static UserVO vo(Long id, String username) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setRole("student");
        return vo;
    }
}
