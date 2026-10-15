package com.util.demo2.user.repository;

import com.util.demo2.user.entity.User;
import com.util.demo2.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserRepository 数据访问层测试 —— <b>真连 MySQL</b>，SQL 真的执行。
 *
 * 为什么要有这一层：Mock 单测（UserServiceTest 等）里 UserRepository 是假的，
 * SQL 写错（列名、逻辑删除条件、LEFT JOIN、LIMIT/OFFSET、null 绑定）根本发现不了。
 * 这一层把每条 SQL 都在真库上跑一遍。
 *
 * 数据安全：类上 @Transactional，Spring 会在每个用例结束后自动回滚，
 * 所以在真库里插入的测试用户/班级不会留下痕迹（每个用例自己造数据）。
 * 前置条件：本机 MySQL 已启动 + 已执行 database/create-database.sql。
 *
 * 运行：mvnw.cmd test -Dtest=UserRepositoryDbTest
 */
@SpringBootTest
@Transactional
class UserRepositoryDbTest {

    /** 本次运行的前缀，避免和真库里的数据、以及上一次运行的数据撞名（username 有唯一索引） */
    private static final String RUN = "it_" + Long.toString(System.currentTimeMillis() % 1000000, 36);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个用例自己造一个班级（班级模块还没合入，这里直接 insert 一行，回滚后自动消失） */
    private Long classId;

    @BeforeEach
    void insertClass() {
        String className = RUN + "_班级";
        jdbcTemplate.update("INSERT INTO `class` (class_name) VALUES (?)", className);
        classId = jdbcTemplate.queryForObject("SELECT id FROM `class` WHERE class_name = ?", Long.class, className);
        assertNotNull(classId);
    }

    // ==================== 新增与查询 ====================

    @Test
    @DisplayName("insert：返回自增主键，create_time/update_time 由数据库默认值填上")
    void insertReturnsGeneratedKey() {
        Long id = userRepository.insert(newUser(RUN + "_s1", "student", classId));

        assertNotNull(id);
        User saved = userRepository.findById(id).orElseThrow();
        assertEquals(RUN + "_s1", saved.getUsername());
        assertEquals("student", saved.getRole());
        assertEquals(classId, saved.getClassId());
        assertEquals(1, saved.getStatus().intValue());
        assertNotNull(saved.getCreateTime(), "create_time 应由 MySQL 默认值 CURRENT_TIMESTAMP 填入");
        assertEquals(0, saved.getIsDeleted().intValue());
    }

    @Test
    @DisplayName("findByUsername：查得到；不存在的账号返回 empty")
    void findByUsername() {
        userRepository.insert(newUser(RUN + "_s2", "student", classId));

        assertTrue(userRepository.findByUsername(RUN + "_s2").isPresent());
        assertTrue(userRepository.findByUsername(RUN + "_nobody").isEmpty());
    }

    @Test
    @DisplayName("class_id 为 NULL 的教师：读出来是 null，不是 0（rs.wasNull 处理正确）")
    void nullClassIdIsReadAsNull() {
        Long id = userRepository.insert(newUser(RUN + "_t1", "teacher", null));

        User teacher = userRepository.findById(id).orElseThrow();
        assertNull(teacher.getClassId(), "教师没有班级，读出来必须是 null 而不是 0");
    }

    @Test
    @DisplayName("findById 查不到已逻辑删除的用户，但行还在库里（is_deleted = 1）")
    void findByIdSkipsDeletedRow() {
        Long id = userRepository.insert(newUser(RUN + "_s3", "student", classId));

        userRepository.logicDelete(id);

        assertTrue(userRepository.findById(id).isEmpty(), "逻辑删除后不应再查得到");
        Integer isDeleted = jdbcTemplate.queryForObject("SELECT is_deleted FROM `user` WHERE id = ?", Integer.class, id);
        assertEquals(1, isDeleted.intValue(), "行必须还在库里，只是标记成已删除");
    }

    // ==================== 唯一性判断 ====================

    @Test
    @DisplayName("existsUsername 只认未删除的用户；existsUsernameIgnoreDeleted 认得被删的同名账号")
    void existsUsernameAndDeletedOccupancy() {
        userRepository.insert(newUser(RUN + "_s4", "student", classId));

        assertTrue(userRepository.existsUsername(RUN + "_s4"));
        assertTrue(userRepository.existsUsernameIgnoreDeleted(RUN + "_s4"));

        Long id = userRepository.findByUsername(RUN + "_s4").orElseThrow().getId();
        userRepository.logicDelete(id);

        assertFalse(userRepository.existsUsername(RUN + "_s4"), "已删除的账号不该算被占用");
        assertTrue(userRepository.existsUsernameIgnoreDeleted(RUN + "_s4"),
                "但唯一索引还占着这个名字，新增同名账号会 1062，所以 Service 要先给出友好提示");
    }

    @Test
    @DisplayName("existsClass：班级存在才为真；班级被逻辑删除后也为假")
    void existsClass() {
        assertTrue(userRepository.existsClass(classId));
        assertFalse(userRepository.existsClass(classId + 999999L));

        jdbcTemplate.update("UPDATE `class` SET is_deleted = 1 WHERE id = ?", classId);
        assertFalse(userRepository.existsClass(classId), "已删除的班级不能再用");
    }

    // ==================== 统计与分页 ====================

    @Test
    @DisplayName("countByRole：只统计未删除的用户")
    void countByRoleIgnoresDeleted() {
        long before = userRepository.countByRole("student");

        Long keep = userRepository.insert(newUser(RUN + "_s5", "student", classId));
        userRepository.insert(newUser(RUN + "_s6", "student", classId));
        userRepository.logicDelete(keep);

        assertEquals(before + 1, userRepository.countByRole("student"), "被逻辑删除的学生不该被统计");
    }

    @Test
    @DisplayName("count + page：四个过滤条件都能用，列表按 id 倒序，LEFT JOIN 带出班级名")
    void pageWithFilters() {
        userRepository.insert(newUser(RUN + "_p1", "student", classId));
        userRepository.insert(newUser(RUN + "_p2", "student", classId));
        Long disabledId = userRepository.insert(newUser(RUN + "_p3", "student", null));
        jdbcTemplate.update("UPDATE `user` SET status = 0 WHERE id = ?", disabledId);

        assertEquals(3, userRepository.count(RUN + "_p", null, null, null));
        assertEquals(2, userRepository.count(RUN + "_p", "student", classId, null));
        assertEquals(1, userRepository.count(null, null, null, 0), "status=0 只应查到刚禁用的那条");
        assertEquals(0, userRepository.count(RUN + "_p", "teacher", null, null));

        List<UserVO> records = userRepository.page(RUN + "_p", null, null, null, 0, 10);
        assertEquals(3, records.size());
        assertEquals(RUN + "_p3", records.get(0).getUsername(), "列表应按 id 倒序（最新建的在前）");
        assertEquals(RUN + "_p1", records.get(2).getUsername());
        assertEquals(RUN + "_班级", records.get(1).getClassName(), "分页 SQL 的 LEFT JOIN 正常");
        assertNull(records.get(0).getClassName(), "没班级的学生，className 应该是 null 而不是空串");

        // LIMIT/OFFSET 生效
        List<UserVO> secondPage = userRepository.page(RUN + "_p", null, null, null, 1, 1);
        assertEquals(1, secondPage.size());
        assertEquals(RUN + "_p2", secondPage.get(0).getUsername());
    }

    @Test
    @DisplayName("分页列表不包含逻辑删除的用户")
    void pageExcludesDeleted() {
        Long id = userRepository.insert(newUser(RUN + "_p4", "student", classId));
        assertEquals(1, userRepository.count(RUN + "_p4", null, null, null));

        userRepository.logicDelete(id);

        assertEquals(0, userRepository.count(RUN + "_p4", null, null, null));
        assertTrue(userRepository.page(RUN + "_p4", null, null, null, 0, 10).isEmpty());
    }

    @Test
    @DisplayName("page 的第 1 页 offset=0：不会漏掉第一条（分页写错最容易在这）")
    void firstPageOffsetZero() {
        userRepository.insert(newUser(RUN + "_o1", "student", classId));

        List<UserVO> page = userRepository.page(RUN + "_o1", null, null, null, 0, 1);

        assertEquals(1, page.size(), "offset=0、size=1 必须能取到第一条");
    }

    // ==================== 更新与逻辑删除 ====================

    @Test
    @DisplayName("update：基础信息落库；class_id 设成 NULL 时真的写 NULL（MySQL 绑定 null 的关键用例）")
    void updateSetsNullClassId() {
        Long id = userRepository.insert(newUser(RUN + "_t2", "student", classId));

        User update = new User();
        update.setId(id);
        update.setRealName("改过的名字");
        update.setRole("teacher");
        update.setClassId(null);
        update.setStatus(0);
        assertEquals(1, userRepository.update(update));

        User after = userRepository.findById(id).orElseThrow();
        assertEquals("改过的名字", after.getRealName());
        assertEquals("teacher", after.getRole());
        assertEquals(0, after.getStatus().intValue());
        assertNull(after.getClassId());
        Integer rawClassId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE id = ? AND class_id IS NULL", Integer.class, id);
        assertEquals(1, rawClassId.intValue(), "库里必须是 SQL NULL");
    }

    @Test
    @DisplayName("updatePassword 只改密码，其他字段不动")
    void updatePasswordOnly() {
        Long id = userRepository.insert(newUser(RUN + "_s7", "student", classId));

        assertEquals(1, userRepository.updatePassword(id, "$2a$10$newhashvalueforintegrationtest000000000000000000000000"));

        User after = userRepository.findById(id).orElseThrow();
        assertEquals("$2a$10$newhashvalueforintegrationtest000000000000000000000000", after.getPassword());
        assertEquals(RUN + "_s7", after.getUsername());
        assertEquals(classId, after.getClassId());
    }

    @Test
    @DisplayName("logicDelete 幂等：已经删过的行再删影响行数为 0")
    void logicDeleteTwiceAffectsNoRow() {
        Long id = userRepository.insert(newUser(RUN + "_s8", "student", classId));

        assertEquals(1, userRepository.logicDelete(id));
        assertEquals(0, userRepository.logicDelete(id), "重复删除不应再影响任何行（Service 会据此返回 400）");
    }

    // ==================== 测试数据工厂 ====================

    private static User newUser(String username, String role, Long classId) {
        User user = new User();
        user.setUsername(username);
        // 仓储层不做加密，这里给一个固定长度的假哈希即可（真加密在 Service 里用 BCrypt）
        user.setPassword("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        user.setRealName("集成测试用户");
        user.setRole(role);
        user.setClassId(classId);
        user.setStatus(1);
        return user;
    }
}
