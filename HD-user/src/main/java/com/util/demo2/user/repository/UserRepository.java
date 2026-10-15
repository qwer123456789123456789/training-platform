package com.util.demo2.user.repository;

import com.util.demo2.user.entity.User;
import com.util.demo2.user.vo.UserVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * user 表的数据访问层，直接用 JdbcTemplate 写 SQL。
 *
 * 为什么不用 MyBatis-Plus：当前工程是 Spring Boot 4.1.1 + Spring Framework 7，
 * MyBatis-Plus 目前主要面向 Boot 3.x，存在自动装配/API 不兼容风险，课程项目里一旦跑不起来很麻烦。
 * 之后如果组里统一改用 MyBatis-Plus，只需要把本文件换成 UserMapper 接口即可，Service 不用动。
 *
 * 注意：所有查询都显式带 is_deleted = 0，不依赖任何框架的逻辑删除自动拼接。
 */
@Repository
public class UserRepository {

    private static final String TABLE = "`user`";
    private static final String COLUMNS =
            "id, username, password, real_name, role, class_id, status, create_time, update_time, is_deleted";

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setRealName(rs.getString("real_name"));
        user.setRole(rs.getString("role"));
        long classId = rs.getLong("class_id");
        user.setClassId(rs.wasNull() ? null : classId);
        user.setStatus(rs.getInt("status"));
        user.setCreateTime(rs.getObject("create_time", LocalDateTime.class));
        user.setUpdateTime(rs.getObject("update_time", LocalDateTime.class));
        user.setIsDeleted(rs.getInt("is_deleted"));
        return user;
    };

    private static final RowMapper<UserVO> VO_MAPPER = (rs, rowNum) -> {
        UserVO vo = new UserVO();
        vo.setId(rs.getLong("id"));
        vo.setUsername(rs.getString("username"));
        vo.setRealName(rs.getString("real_name"));
        vo.setRole(rs.getString("role"));
        long classId = rs.getLong("class_id");
        vo.setClassId(rs.wasNull() ? null : classId);
        vo.setClassName(rs.getString("class_name"));
        vo.setStatus(rs.getInt("status"));
        vo.setCreateTime(UserVO.format(rs.getObject("create_time", LocalDateTime.class)));
        vo.setUpdateTime(UserVO.format(rs.getObject("update_time", LocalDateTime.class)));
        return vo;
    };

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findById(Long id) {
        List<User> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", USER_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 登录用：按账号查未删除的用户 */
    public Optional<User> findByUsername(String username) {
        List<User> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE username = ? AND is_deleted = 0", USER_MAPPER, username);
        return list.stream().findFirst();
    }

    /** 账号是否已被占用（未删除的用户） */
    public boolean existsUsername(String username) {
        return countBySql("SELECT COUNT(*) FROM " + TABLE + " WHERE username = ? AND is_deleted = 0", username) > 0;
    }

    /** 账号是否被"已逻辑删除"的用户占用：username 有唯一索引，删除后名字仍被占着 */
    public boolean existsUsernameIgnoreDeleted(String username) {
        return countBySql("SELECT COUNT(*) FROM " + TABLE + " WHERE username = ?", username) > 0;
    }

    /** 班级是否存在（class 表由班级模块负责，这里只做一次存在性校验，防止写入无效 classId） */
    public boolean existsClass(Long classId) {
        return countBySql("SELECT COUNT(*) FROM `class` WHERE id = ? AND is_deleted = 0", classId) > 0;
    }

    /** 管理员数量，用于"不能删除/降级最后一个管理员"的校验 */
    public long countByRole(String role) {
        return countBySql("SELECT COUNT(*) FROM " + TABLE + " WHERE role = ? AND is_deleted = 0", role);
    }

    public long count(String username, String role, Long classId, Integer status) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM " + TABLE + " u" + whereClause(username, role, classId, status, args);
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<UserVO> page(String username, String role, Long classId, Integer status, long offset, long size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder()
                .append("SELECT u.id, u.username, u.real_name, u.role, u.class_id, u.status, u.create_time, u.update_time, ")
                .append("c.class_name AS class_name ")
                .append("FROM ").append(TABLE).append(" u ")
                .append("LEFT JOIN `class` c ON c.id = u.class_id AND c.is_deleted = 0")
                .append(whereClause(username, role, classId, status, args))
                .append(" ORDER BY u.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), VO_MAPPER, args.toArray());
    }

    /** 新增用户，返回自增主键 */
    public Long insert(User user) {
        String sql = "INSERT INTO " + TABLE
                + " (username, password, real_name, role, class_id, status) VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRealName());
            ps.setString(4, user.getRole());
            if (user.getClassId() == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, user.getClassId());
            }
            ps.setInt(6, user.getStatus() == null ? 1 : user.getStatus());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /** 修改用户基础信息（不含密码）。class_id 为 null 时显式 setNull，避免不同驱动对"无类型 null"的处理差异 */
    public int update(User user) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE
                        + " SET real_name = ?, role = ?, class_id = ?, status = ? WHERE id = ? AND is_deleted = 0",
                ps -> {
                    ps.setString(1, user.getRealName());
                    ps.setString(2, user.getRole());
                    if (user.getClassId() == null) {
                        ps.setNull(3, Types.BIGINT);
                    } else {
                        ps.setLong(3, user.getClassId());
                    }
                    ps.setInt(4, user.getStatus() == null ? 1 : user.getStatus());
                    ps.setLong(5, user.getId());
                });
    }

    public int updatePassword(Long id, String encodedPassword) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET password = ? WHERE id = ? AND is_deleted = 0",
                encodedPassword, id);
    }

    /** 逻辑删除 */
    public int logicDelete(Long id) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE id = ? AND is_deleted = 0", id);
    }

    private String whereClause(String username, String role, Long classId, Integer status, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE u.is_deleted = 0");
        if (username != null && !username.isBlank()) {
            sb.append(" AND u.username LIKE ?");
            args.add("%" + username.trim() + "%");
        }
        if (role != null && !role.isBlank()) {
            sb.append(" AND u.role = ?");
            args.add(role.trim());
        }
        if (classId != null) {
            sb.append(" AND u.class_id = ?");
            args.add(classId);
        }
        if (status != null) {
            sb.append(" AND u.status = ?");
            args.add(status);
        }
        return sb.toString();
    }

    private long countBySql(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }
}
