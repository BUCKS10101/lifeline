package com.personalos.backend.tasks;

import com.personalos.backend.tasks.dto.TaskDtos.GoalRef;
import com.personalos.backend.tasks.dto.TaskDtos.TaskResponse;
import com.personalos.backend.tasks.dto.TaskDtos.TaskSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All SQL for tasks. Every statement filters by the owner's id, so one person's tasks are never reachable by another.
 * Overdue is worked out here from the date and the caller's today; nothing derived is stored.
 */
@Repository
public class TaskRepository {

    private static final String SELECT = """
            SELECT t.id, t.title, t.notes, t.due_date, t.priority, t.completed_at, t.goal_id, g.title AS goal_title
            FROM tasks t LEFT JOIN goals g ON g.id = t.goal_id AND g.user_id = t.user_id
            """;

    /** The order inside each view is fixed: date, then priority (high first), then when it was added. */
    private static final String PRIORITY_RANK = "CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END";

    private final JdbcClient jdbc;

    public TaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static TaskResponse map(java.sql.ResultSet rs, LocalDate today) throws java.sql.SQLException {
        LocalDate due = rs.getObject("due_date", LocalDate.class);
        Timestamp completed = rs.getTimestamp("completed_at");
        UUID goalId = rs.getObject("goal_id", UUID.class);
        return new TaskResponse(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("notes"), due,
                Priority.valueOf(rs.getString("priority")), completed == null ? null : completed.toInstant(),
                completed == null && due != null && due.isBefore(today),
                goalId == null ? null : new GoalRef(goalId, rs.getString("goal_title")));
    }

    public Optional<TaskResponse> find(UUID userId, UUID id, LocalDate today) {
        return jdbc.sql(SELECT + " WHERE t.id = :id AND t.user_id = :userId")
                .param("id", id).param("userId", userId)
                .query((rs, n) -> map(rs, today)).optional();
    }

    /** The WHERE and ORDER BY for a view. The date is the caller's today. */
    private static String where(TaskView view) {
        return switch (view) {
            case TODAY -> "t.completed_at IS NULL AND t.due_date <= :today";
            case UPCOMING -> "t.completed_at IS NULL AND t.due_date > :today";
            case ANYTIME -> "t.completed_at IS NULL AND t.due_date IS NULL";
            case DONE -> "t.completed_at IS NOT NULL";
        };
    }

    private static String order(TaskView view) {
        return switch (view) {
            case TODAY, UPCOMING -> "t.due_date, " + PRIORITY_RANK + ", t.created_at, t.id";
            case ANYTIME -> PRIORITY_RANK + ", t.created_at, t.id";
            case DONE -> "t.completed_at DESC, t.id";
        };
    }

    public List<TaskResponse> list(UUID userId, TaskView view, UUID goalId, LocalDate today, int limit, long offset) {
        return jdbc.sql(SELECT + " WHERE t.user_id = :userId AND " + where(view)
                        + " AND (CAST(:goalId AS uuid) IS NULL OR t.goal_id = CAST(:goalId AS uuid))"
                        + " ORDER BY " + order(view) + " LIMIT :limit OFFSET :offset")
                .param("userId", userId).param("today", today).param("goalId", goalId, java.sql.Types.OTHER)
                .param("limit", limit).param("offset", offset)
                .query((rs, n) -> map(rs, today)).list();
    }

    public long count(UUID userId, TaskView view, UUID goalId, LocalDate today) {
        return jdbc.sql("SELECT COUNT(*) FROM tasks t WHERE t.user_id = :userId AND " + where(view)
                        + " AND (CAST(:goalId AS uuid) IS NULL OR t.goal_id = CAST(:goalId AS uuid))")
                .param("userId", userId).param("today", today).param("goalId", goalId, java.sql.Types.OTHER)
                .query(Long.class).single();
    }

    public TaskSummary summary(UUID userId, LocalDate today) {
        return jdbc.sql("""
                        SELECT COUNT(*) FILTER (WHERE due_date = :today) AS due_today,
                               COUNT(*) FILTER (WHERE due_date < :today) AS overdue,
                               COUNT(*) AS open
                        FROM tasks WHERE user_id = :userId AND completed_at IS NULL
                        """)
                .param("userId", userId).param("today", today)
                .query((rs, n) -> new TaskSummary(rs.getInt("due_today"), rs.getInt("overdue"), rs.getInt("open")))
                .single();
    }

    /** Whether the goal exists and belongs to this person. */
    public boolean ownsGoal(UUID userId, UUID goalId) {
        return jdbc.sql("SELECT COUNT(*) FROM goals WHERE id = :id AND user_id = :userId")
                .param("id", goalId).param("userId", userId).query(Long.class).single() > 0;
    }

    /**
     * Adds the task unless the client-generated id already exists, atomically. Returns 1 when added and 0 when the id
     * was taken, which makes a retry (or a double tap) unable to add it twice.
     */
    public int insertIfAbsent(UUID id, UUID userId, UUID goalId, String title, String notes, LocalDate dueDate, Priority priority, Instant now) {
        return jdbc.sql("""
                        INSERT INTO tasks (id, user_id, goal_id, title, notes, due_date, priority, created_at, updated_at)
                        VALUES (:id, :userId, :goalId, :title, :notes, :dueDate, :priority, :now, :now)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", id).param("userId", userId).param("goalId", goalId, java.sql.Types.OTHER).param("title", title)
                .param("notes", notes, java.sql.Types.VARCHAR).param("dueDate", dueDate, java.sql.Types.DATE)
                .param("priority", priority.name()).param("now", Timestamp.from(now))
                .update();
    }

    /** The owner of a task id, if it exists (used to tell a retry of my own request from someone else's id). */
    public Optional<UUID> ownerOf(UUID id) {
        return jdbc.sql("SELECT user_id FROM tasks WHERE id = :id").param("id", id).query(UUID.class).optional();
    }

    /** Replaces every editable field. The caller has already merged the change into the current values. Returns rows changed (0 or 1). */
    public int update(UUID id, UUID userId, UUID goalId, String title, String notes, LocalDate dueDate, Priority priority, Instant now) {
        return jdbc.sql("""
                        UPDATE tasks SET goal_id = :goalId, title = :title, notes = :notes, due_date = :dueDate,
                                         priority = :priority, updated_at = :now
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("goalId", goalId, java.sql.Types.OTHER).param("title", title)
                .param("notes", notes, java.sql.Types.VARCHAR).param("dueDate", dueDate, java.sql.Types.DATE)
                .param("priority", priority.name()).param("now", Timestamp.from(now))
                .update();
    }

    /** Marks it done. Completing a done task changes nothing (its original completion moment is kept). Returns rows matched. */
    public int complete(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE tasks SET completed_at = COALESCE(completed_at, :now),
                                         updated_at = CASE WHEN completed_at IS NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now))
                .update();
    }

    /** Marks it not done. Reopening an open task changes nothing. Returns rows matched. */
    public int reopen(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE tasks SET completed_at = NULL,
                                         updated_at = CASE WHEN completed_at IS NOT NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now))
                .update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM tasks WHERE id = :id AND user_id = :userId").param("id", id).param("userId", userId).update();
    }
}
