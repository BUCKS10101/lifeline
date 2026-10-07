package com.personalos.backend.reminders;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All SQL for reminders. Every statement filters by the owner's id. */
@Repository
public class ReminderRepository {

    private static final String SELECT = "SELECT id, title, remind_at, zone_id, completed_at FROM reminders";

    private final JdbcClient jdbc;

    public ReminderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID id, String title, Instant remindAt, String zoneId, Instant completedAt) {}

    private static Row map(ResultSet rs, int n) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new Row(rs.getObject("id", UUID.class), rs.getString("title"), rs.getTimestamp("remind_at").toInstant(),
                rs.getString("zone_id"), completed == null ? null : completed.toInstant());
    }

    public Optional<Row> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).query(ReminderRepository::map).optional();
    }

    /** Pending reminders first (soonest first), then completed ones (when asked for) newest-completed first. */
    public List<Row> list(UUID userId, boolean includeCompleted) {
        return jdbc.sql(SELECT + " WHERE user_id = :userId AND (:includeCompleted OR completed_at IS NULL)"
                        + " ORDER BY (completed_at IS NOT NULL), CASE WHEN completed_at IS NULL THEN remind_at END, completed_at DESC")
                .param("userId", userId).param("includeCompleted", includeCompleted)
                .query(ReminderRepository::map).list();
    }

    public UUID insert(UUID userId, String title, Instant remindAt, String zoneId, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reminders (id, user_id, title, remind_at, zone_id, created_at, updated_at)
                        VALUES (:id, :userId, :title, :remindAt, :zoneId, :now, :now)
                        """)
                .param("id", id).param("userId", userId).param("title", title).param("remindAt", Timestamp.from(remindAt))
                .param("zoneId", zoneId).param("now", Timestamp.from(now)).update();
        return id;
    }

    /** Replaces title and remind_at (the zone never changes after creation). Returns rows changed (0 or 1). */
    public int update(UUID id, UUID userId, String title, Instant remindAt, Instant now) {
        return jdbc.sql("UPDATE reminders SET title = :title, remind_at = :remindAt, updated_at = :now WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).param("title", title).param("remindAt", Timestamp.from(remindAt))
                .param("now", Timestamp.from(now)).update();
    }

    /** Idempotent: completing an already-completed reminder keeps its original moment. */
    public int complete(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE reminders SET completed_at = COALESCE(completed_at, :now),
                                              updated_at = CASE WHEN completed_at IS NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now)).update();
    }

    /** Idempotent: reopening a pending reminder changes nothing. */
    public int reopen(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE reminders SET completed_at = NULL,
                                              updated_at = CASE WHEN completed_at IS NOT NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now)).update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM reminders WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).update();
    }
}
