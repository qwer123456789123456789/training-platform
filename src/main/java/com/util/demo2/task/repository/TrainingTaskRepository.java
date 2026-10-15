package com.util.demo2.task.repository;

import com.util.demo2.task.entity.TrainingTask;
import com.util.demo2.task.vo.TaskVO;
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
 * training_task 表的数据访问层，直接用 JdbcTemplate 写 SQL（与后端 1 的 UserRepository 同一套约定）。
 * 所有查询都显式带 is_deleted = 0。
 */
@Repository
public class TrainingTaskRepository {

    private static final String TABLE = "`training_task`";
    private static final String COLUMNS =
            "id, task_name, task_desc, class_id, total_score, deadline, status, create_user_id, create_time, update_time, is_deleted";

    private static final RowMapper<TrainingTask> ENTITY_MAPPER = (rs, rowNum) -> {
        TrainingTask t = new TrainingTask();
        t.setId(rs.getLong("id"));
        t.setTaskName(rs.getString("task_name"));
        t.setTaskDesc(rs.getString("task_desc"));
        t.setClassId(rs.getLong("class_id"));
        int totalScore = rs.getInt("total_score");
        t.setTotalScore(rs.wasNull() ? null : totalScore);
        t.setDeadline(rs.getObject("deadline", LocalDateTime.class));
        t.setStatus(rs.getString("status"));
        t.setCreateUserId(rs.getLong("create_user_id"));
        t.setCreateTime(rs.getObject("create_time", LocalDateTime.class));
        t.setUpdateTime(rs.getObject("update_time", LocalDateTime.class));
        t.setIsDeleted(rs.getInt("is_deleted"));
        return t;
    };

    private static final RowMapper<TaskVO> VO_MAPPER = (rs, rowNum) -> {
        TaskVO vo = new TaskVO();
        vo.setId(rs.getLong("id"));
        vo.setTaskName(rs.getString("task_name"));
        vo.setTaskDesc(rs.getString("task_desc"));
        vo.setClassId(rs.getLong("class_id"));
        vo.setClassName(rs.getString("class_name"));
        int totalScore = rs.getInt("total_score");
        vo.setTotalScore(rs.wasNull() ? null : totalScore);
        vo.setDeadline(TaskVO.format(rs.getObject("deadline", LocalDateTime.class)));
        vo.setStatus(rs.getString("status"));
        vo.setCreateUserId(rs.getLong("create_user_id"));
        vo.setCreateTime(TaskVO.format(rs.getObject("create_time", LocalDateTime.class)));
        vo.setUpdateTime(TaskVO.format(rs.getObject("update_time", LocalDateTime.class)));
        return vo;
    };

    private final JdbcTemplate jdbcTemplate;

    public TrainingTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TrainingTask> findById(Long id) {
        List<TrainingTask> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", ENTITY_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 班级是否存在（class 表由班级模块负责，这里只做存在性校验，防止写入无效 classId） */
    public boolean existsClass(Long classId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `class` WHERE id = ? AND is_deleted = 0", Long.class, classId);
        return count != null && count > 0;
    }

    public long count(Long classId, String taskName) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM " + TABLE + " t" + whereClause(classId, taskName, args);
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<TaskVO> page(Long classId, String taskName, long offset, long size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder()
                .append("SELECT t.id, t.task_name, t.task_desc, t.class_id, c.class_name AS class_name, ")
                .append("t.total_score, t.deadline, t.status, t.create_user_id, t.create_time, t.update_time ")
                .append("FROM ").append(TABLE).append(" t ")
                .append("LEFT JOIN `class` c ON c.id = t.class_id AND c.is_deleted = 0")
                .append(whereClause(classId, taskName, args))
                .append(" ORDER BY t.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), VO_MAPPER, args.toArray());
    }

    /** 发布任务，返回自增主键 */
    public Long insert(TrainingTask t) {
        String sql = "INSERT INTO " + TABLE
                + " (task_name, task_desc, class_id, total_score, deadline, status, create_user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, t.getTaskName());
            ps.setString(2, t.getTaskDesc());
            ps.setLong(3, t.getClassId());
            if (t.getTotalScore() == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, t.getTotalScore());
            }
            if (t.getDeadline() == null) {
                ps.setNull(5, Types.TIMESTAMP);
            } else {
                ps.setObject(5, t.getDeadline());
            }
            ps.setString(6, t.getStatus());
            ps.setLong(7, t.getCreateUserId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int update(TrainingTask t) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE
                        + " SET task_name = ?, task_desc = ?, class_id = ?, total_score = ?, deadline = ?, status = ? "
                        + "WHERE id = ? AND is_deleted = 0",
                ps -> {
                    ps.setString(1, t.getTaskName());
                    ps.setString(2, t.getTaskDesc());
                    ps.setLong(3, t.getClassId());
                    if (t.getTotalScore() == null) {
                        ps.setNull(4, Types.INTEGER);
                    } else {
                        ps.setInt(4, t.getTotalScore());
                    }
                    if (t.getDeadline() == null) {
                        ps.setNull(5, Types.TIMESTAMP);
                    } else {
                        ps.setObject(5, t.getDeadline());
                    }
                    ps.setString(6, t.getStatus());
                    ps.setLong(7, t.getId());
                });
    }

    /** 逻辑删除 */
    public int logicDelete(Long id) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE id = ? AND is_deleted = 0", id);
    }

    private String whereClause(Long classId, String taskName, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE t.is_deleted = 0");
        if (classId != null) {
            sb.append(" AND t.class_id = ?");
            args.add(classId);
        }
        if (taskName != null && !taskName.isBlank()) {
            sb.append(" AND t.task_name LIKE ?");
            args.add("%" + taskName.trim() + "%");
        }
        return sb.toString();
    }
}
