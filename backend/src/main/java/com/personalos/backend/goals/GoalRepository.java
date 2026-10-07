package com.personalos.backend.goals;

import com.personalos.backend.goals.dto.GoalDtos.GoalResponse;
import com.personalos.backend.goals.dto.GoalDtos.GoalTaskItem;
import com.personalos.backend.tasks.Priority;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All SQL for goals. Every statement filters by the owner's id, so one person's goals are never reachable by another.
 * Task and habit counts are derived here at read time; nothing derived is stored. Deleting a goal relies on the
 * database's own {@code ON DELETE SET NULL} on {@code tasks.goal_id} and {@code habits.goal_id} to unlink its work,
 * so a plain delete of the goal row is all that is needed.
 */
@Repository
public class GoalRepository {

    private static final String SELECT = """
            SELECT g.id, g.title, g.description, g.target_date, g.status, g.achieved_at,
                   (SELECT COUNT(*) FROM tasks t WHERE t.goal_id = g.id) AS task_count,
                   (SELECT COUNT(*) FROM tasks t WHERE t.goal_id = g.id AND t.completed_at IS NOT NULL) AS done_count
            FROM goals g
            """;

    private final JdbcClient jdbc;

    public GoalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static GoalResponse map(ResultSet rs) throws SQLException {
        int taskCount = rs.getInt("task_count");
        int doneCount = rs.getInt("done_count");
        Timestamp achieved = rs.getTimestamp("achieved_at");
        return new GoalResponse(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getObject("target_date", LocalDate.class), GoalStatus.valueOf(rs.getString("status")),
                achieved == null ? null : achieved.toInstant(), taskCount, doneCount, GoalProgress.percent(taskCount, doneCount));
    }

    public Optional<GoalResponse> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + " WHERE g.id = :id AND g.user_id = :userId")
                .param("id", id).param("userId", userId).query((rs, n) -> map(rs)).optional();
    }

    /** Active goals soonest-deadline first; achieved and archived goals most-recent-first. */
    public List<GoalResponse> list(UUID userId, GoalStatus status) {
        String order = status == GoalStatus.ACTIVE ? "g.target_date NULLS LAST, g.created_at, g.id" : "g.updated_at DESC, g.id";
        return jdbc.sql(SELECT + " WHERE g.user_id = :userId AND g.status = :status ORDER BY " + order)
                .param("userId", userId).param("status", status.name())
                .query((rs, n) -> map(rs)).list();
    }

    public UUID insert(UUID userId, String title, String description, LocalDate targetDate, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO goals (id, user_id, title, description, target_date, status, created_at, updated_at)
                        VALUES (:id, :userId, :title, :description, :targetDate, 'ACTIVE', :now, :now)
                        """)
                .param("id", id).param("userId", userId).param("title", title)
                .param("description", description, Types.VARCHAR).param("targetDate", targetDate, Types.DATE)
                .param("now", Timestamp.from(now)).update();
        return id;
    }

    /** Replaces every editable field. The caller has already merged the change and worked out {@code achievedAt}. Returns rows changed. */
    public int update(UUID id, UUID userId, String title, String description, LocalDate targetDate, GoalStatus status, Instant achievedAt, Instant now) {
        return jdbc.sql("""
                        UPDATE goals SET title = :title, description = :description, target_date = :targetDate,
                                         status = :status, achieved_at = :achievedAt, updated_at = :now
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("title", title)
                .param("description", description, Types.VARCHAR).param("targetDate", targetDate, Types.DATE)
                .param("status", status.name()).param("achievedAt", achievedAt == null ? null : Timestamp.from(achievedAt), Types.TIMESTAMP)
                .param("now", Timestamp.from(now)).update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM goals WHERE id = :id AND user_id = :userId").param("id", id).param("userId", userId).update();
    }

    /** This goal's linked tasks, open first, then done (most recently completed first within each group). */
    public List<GoalTaskItem> linkedTasks(UUID userId, UUID goalId, LocalDate today) {
        return jdbc.sql("""
                        SELECT id, title, due_date, priority, completed_at
                        FROM tasks WHERE user_id = :userId AND goal_id = :goalId
                        ORDER BY (completed_at IS NOT NULL), due_date NULLS LAST, completed_at DESC, created_at
                        """)
                .param("userId", userId).param("goalId", goalId)
                .query((rs, n) -> {
                    LocalDate due = rs.getObject("due_date", LocalDate.class);
                    Timestamp completed = rs.getTimestamp("completed_at");
                    return new GoalTaskItem(rs.getObject("id", UUID.class), rs.getString("title"), due,
                            Priority.valueOf(rs.getString("priority")), completed == null ? null : completed.toInstant(),
                            completed == null && due != null && due.isBefore(today));
                }).list();
    }

    public record LinkedHabitRow(UUID id, String name, boolean archived, int daysOfWeek, LocalDate startedOn) {}

    /** This goal's linked habits, alphabetical. Streaks are computed by the caller from each habit's completions. */
    public List<LinkedHabitRow> linkedHabits(UUID userId, UUID goalId) {
        return jdbc.sql("""
                        SELECT id, name, archived_at, days_of_week, started_on
                        FROM habits WHERE user_id = :userId AND goal_id = :goalId ORDER BY lower(name)
                        """)
                .param("userId", userId).param("goalId", goalId)
                .query((rs, n) -> new LinkedHabitRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getTimestamp("archived_at") != null, rs.getInt("days_of_week"), rs.getObject("started_on", LocalDate.class)))
                .list();
    }
}
