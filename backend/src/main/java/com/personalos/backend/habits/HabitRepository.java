package com.personalos.backend.habits;

import com.personalos.backend.habits.dto.HabitDtos.GoalRef;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * All SQL for habits and their completions. Every statement filters by the owner's id, so one person's habits are
 * never reachable by another. A completion has no user_id of its own: every completion statement joins through (or is
 * scoped to) a habit that has already been confirmed to belong to the caller, so it can only ever be reached that way.
 */
@Repository
public class HabitRepository {

    private static final String SELECT = """
            SELECT h.id, h.name, h.days_of_week, h.started_on, h.archived_at, h.goal_id, g.title AS goal_title
            FROM habits h LEFT JOIN goals g ON g.id = h.goal_id AND g.user_id = h.user_id
            """;

    private final JdbcClient jdbc;

    public HabitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record HabitRow(UUID id, String name, int daysOfWeek, LocalDate startedOn, boolean archived, GoalRef goal) {
    }

    private static HabitRow map(ResultSet rs) throws SQLException {
        UUID goalId = rs.getObject("goal_id", UUID.class);
        return new HabitRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("days_of_week"),
                rs.getObject("started_on", LocalDate.class), rs.getTimestamp("archived_at") != null,
                goalId == null ? null : new GoalRef(goalId, rs.getString("goal_title")));
    }

    public Optional<HabitRow> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + " WHERE h.id = :id AND h.user_id = :userId")
                .param("id", id).param("userId", userId).query((rs, n) -> map(rs)).optional();
    }

    /** Active habits first, then archived ones (only present when asked for); alphabetical within each group. */
    public List<HabitRow> list(UUID userId, boolean includeArchived) {
        return jdbc.sql(SELECT + " WHERE h.user_id = :userId AND (:includeArchived OR h.archived_at IS NULL)"
                        + " ORDER BY (h.archived_at IS NOT NULL), lower(h.name)")
                .param("userId", userId).param("includeArchived", includeArchived)
                .query((rs, n) -> map(rs)).list();
    }

    /** Every completion of every one of this person's habits, oldest first, grouped by habit. One query, not one per habit. */
    public Map<UUID, List<LocalDate>> completionsByHabit(UUID userId) {
        List<Map.Entry<UUID, LocalDate>> rows = jdbc.sql("""
                        SELECT hc.habit_id, hc.completed_on FROM habit_completions hc
                        JOIN habits h ON h.id = hc.habit_id WHERE h.user_id = :userId ORDER BY hc.completed_on
                        """)
                .param("userId", userId)
                .query((rs, n) -> Map.entry(rs.getObject("habit_id", UUID.class), rs.getObject("completed_on", LocalDate.class)))
                .list();
        Map<UUID, List<LocalDate>> out = new HashMap<>();
        for (var row : rows) out.computeIfAbsent(row.getKey(), k -> new ArrayList<>()).add(row.getValue());
        return out;
    }

    /** Every completion of one habit, oldest first. The caller has already confirmed ownership. */
    public List<LocalDate> completions(UUID habitId) {
        return jdbc.sql("SELECT completed_on FROM habit_completions WHERE habit_id = :habitId ORDER BY completed_on")
                .param("habitId", habitId).query(LocalDate.class).list();
    }

    public Optional<LocalDate> earliestCompletion(UUID habitId) {
        return jdbc.sql("SELECT MIN(completed_on) AS m FROM habit_completions WHERE habit_id = :habitId")
                .param("habitId", habitId).query((rs, n) -> rs.getObject("m", LocalDate.class)).optional()
                .filter(Objects::nonNull);
    }

    public boolean ownsGoal(UUID userId, UUID goalId) {
        return jdbc.sql("SELECT COUNT(*) FROM goals WHERE id = :id AND user_id = :userId")
                .param("id", goalId).param("userId", userId).query(Long.class).single() > 0;
    }

    public UUID insert(UUID userId, UUID goalId, String name, int daysOfWeek, LocalDate startedOn, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO habits (id, user_id, goal_id, name, days_of_week, started_on, created_at, updated_at)
                        VALUES (:id, :userId, :goalId, :name, :daysOfWeek, :startedOn, :now, :now)
                        """)
                .param("id", id).param("userId", userId).param("goalId", goalId, Types.OTHER).param("name", name)
                .param("daysOfWeek", daysOfWeek).param("startedOn", startedOn).param("now", Timestamp.from(now))
                .update();
        return id;
    }

    /** Replaces every editable field. The caller has already merged the change into the current values. Returns rows changed. */
    public int update(UUID id, UUID userId, UUID goalId, String name, int daysOfWeek, LocalDate startedOn, Instant now) {
        return jdbc.sql("""
                        UPDATE habits SET goal_id = :goalId, name = :name, days_of_week = :daysOfWeek,
                                          started_on = :startedOn, updated_at = :now
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("goalId", goalId, Types.OTHER).param("name", name)
                .param("daysOfWeek", daysOfWeek).param("startedOn", startedOn).param("now", Timestamp.from(now))
                .update();
    }

    public int setArchived(UUID id, UUID userId, boolean archived, Instant now) {
        return jdbc.sql("UPDATE habits SET archived_at = :archivedAt, updated_at = :now WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId)
                .param("archivedAt", archived ? Timestamp.from(now) : null, Types.TIMESTAMP)
                .param("now", Timestamp.from(now)).update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM habits WHERE id = :id AND user_id = :userId").param("id", id).param("userId", userId).update();
    }

    /** Atomic upsert: returns 1 when the day was newly ticked, 0 when it was already ticked. */
    public int tick(UUID habitId, LocalDate date, Instant now) {
        return jdbc.sql("""
                        INSERT INTO habit_completions (habit_id, completed_on, created_at) VALUES (:habitId, :date, :now)
                        ON CONFLICT (habit_id, completed_on) DO NOTHING
                        """)
                .param("habitId", habitId).param("date", date).param("now", Timestamp.from(now)).update();
    }

    /** Returns 1 when a completion was removed, 0 when the day was not ticked. */
    public int untick(UUID habitId, LocalDate date) {
        return jdbc.sql("DELETE FROM habit_completions WHERE habit_id = :habitId AND completed_on = :date")
                .param("habitId", habitId).param("date", date).update();
    }
}
