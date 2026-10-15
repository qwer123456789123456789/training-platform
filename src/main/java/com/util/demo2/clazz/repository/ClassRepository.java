package com.util.demo2.clazz.repository;

import com.util.demo2.clazz.entity.ClassInfo;
import com.util.demo2.clazz.vo.ClassVO;
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
 * class 表的数据访问层，直接用 JdbcTemplate 写 SQL（与后端 1 的 UserRepository 同一套约定）。
 * 所有查询都显式带 is_deleted = 0，不依赖任何框架的逻辑删除自动拼接。
 */
@Repository
public class ClassRepository {

    private static final String TABLE = "`class`";
    private static final String COLUMNS =
            "id, class_name, teacher_id, create_time, update_time, is_deleted";

    private static final RowMapper<ClassInfo> ENTITY_MAPPER = (rs, rowNum) -> {
        ClassInfo c = new ClassInfo();
        c.setId(rs.getLong("id"));
        c.setClassName(rs.getString("class_name"));
        long teacherId = rs.getLong("teacher_id");
        c.setTeacherId(rs.wasNull() ? null : teacherId);
        c.setCreateTime(rs.getObject("create_time", LocalDateTime.class));
        c.setUpdateTime(rs.getObject("update_time", LocalDateTime.class));
        c.setIsDeleted(rs.getInt("is_deleted"));
        return c;
    };

    private static final RowMapper<ClassVO> VO_MAPPER = (rs, rowNum) -> {
        ClassVO vo = new ClassVO();
        vo.setId(rs.getLong("id"));
        vo.setClassName(rs.getString("class_name"));
        long teacherId = rs.getLong("teacher_id");
        vo.setTeacherId(rs.wasNull() ? null : teacherId);
        vo.setTeacherName(rs.getString("teacher_name"));
        vo.setCreateTime(ClassVO.format(rs.getObject("create_time", LocalDateTime.class)));
        vo.setUpdateTime(ClassVO.format(rs.getObject("update_time", LocalDateTime.class)));
        return vo;
    };

    private final JdbcTemplate jdbcTemplate;

    public ClassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ClassInfo> findById(Long id) {
        List<ClassInfo> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", ENTITY_MAPPER, id);
        return list.stream().findFirst();
    }

    public boolean existsById(Long id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", Long.class, id);
        return count != null && count > 0;
    }

    /** 负责教师是否存在：user 表里 role=teacher 且未删除 */
    public boolean existsTeacher(Long teacherId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE id = ? AND role = 'teacher' AND is_deleted = 0", Long.class, teacherId);
        return count != null && count > 0;
    }

    public long count(String className) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM " + TABLE + " c" + whereClause(className, args);
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<ClassVO> page(String className, long offset, long size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder()
                .append("SELECT c.id, c.class_name, c.teacher_id, u.real_name AS teacher_name, c.create_time, c.update_time ")
                .append("FROM ").append(TABLE).append(" c ")
                .append("LEFT JOIN `user` u ON u.id = c.teacher_id AND u.is_deleted = 0")
                .append(whereClause(className, args))
                .append(" ORDER BY c.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), VO_MAPPER, args.toArray());
    }

    /** 新增班级，返回自增主键 */
    public Long insert(ClassInfo c) {
        String sql = "INSERT INTO " + TABLE + " (class_name, teacher_id) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, c.getClassName());
            if (c.getTeacherId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, c.getTeacherId());
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int update(ClassInfo c) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET class_name = ?, teacher_id = ? WHERE id = ? AND is_deleted = 0",
                ps -> {
                    ps.setString(1, c.getClassName());
                    if (c.getTeacherId() == null) {
                        ps.setNull(2, Types.BIGINT);
                    } else {
                        ps.setLong(2, c.getTeacherId());
                    }
                    ps.setLong(3, c.getId());
                });
    }

    /** 逻辑删除 */
    public int logicDelete(Long id) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE id = ? AND is_deleted = 0", id);
    }

    private String whereClause(String className, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE c.is_deleted = 0");
        if (className != null && !className.isBlank()) {
            sb.append(" AND c.class_name LIKE ?");
            args.add("%" + className.trim() + "%");
        }
        return sb.toString();
    }
}
