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

/**
 * All SQL for reminders. A reminder is a calendar event, so this reads and writes {@code calendar_events}: anything
 * added on the calendar also lists here, and anything added here also shows on the calendar. Every statement filters
 * by the owner's id.
 */
@Repository
public class ReminderRepository {

    private static final String SELECT = "SELECT id, title, start_at, zone_id, completed_at, all_day FROM calendar_events";

    private final JdbcClient jdbc;

    public ReminderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID id, String title, Instant remindAt, String zoneId, Instant completedAt, boolean allDay) {}

    private static Row map(ResultSet rs, int n) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new Row(rs.getObject("id", UUID.class), rs.getString("title"), rs.getTimestamp("start_at").toInstant(),
                rs.getString("zone_id"), completed == null ? null : completed.toInstant(), rs.getBoolean("all_day"));
    }

    public Optional<Row> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).query(ReminderRepository::map).optional();
    }

    /** Pending items first (soonest first), then completed ones (when asked for) newest-completed first. */
    public List<Row> list(UUID userId, boolean includeCompleted) {
        return jdbc.sql(SELECT + " WHERE user_id = :userId AND (:includeCompleted OR completed_at IS NULL)"
                        + " ORDER BY (completed_at IS NOT NULL), CASE WHEN completed_at IS NULL THEN start_at END, completed_at DESC")
                .param("userId", userId).param("includeCompleted", includeCompleted)
                .query(ReminderRepository::map).list();
    }

    public UUID insert(UUID userId, String title, Instant remindAt, String zoneId, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO calendar_events (id, user_id, title, start_at, zone_id, all_day, created_at, updated_at)
                        VALUES (:id, :userId, :title, :remindAt, :zoneId, FALSE, :now, :now)
                        """)
                .param("id", id).param("userId", userId).param("title", title).param("remindAt", Timestamp.from(remindAt))
                .param("zoneId", zoneId).param("now", Timestamp.from(now)).update();
        return id;
    }

    /**
     * Replaces title and start (the zone never changes after creation). An event with an end keeps its length: the
     * end moves with the start. Editing the time makes an all-day event a timed one. Returns rows changed (0 or 1).
     */
    public int update(UUID id, UUID userId, String title, Instant remindAt, Instant now) {
        return jdbc.sql("""
                        UPDATE calendar_events SET title = :title, updated_at = :now, all_day = FALSE,
                                                    end_at = CASE WHEN end_at IS NULL THEN NULL
                                                                  ELSE end_at + (CAST(:remindAt AS timestamptz) - start_at) END,
                                                    start_at = :remindAt
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("title", title).param("remindAt", Timestamp.from(remindAt))
                .param("now", Timestamp.from(now)).update();
    }

    /** Idempotent: completing an already-completed item keeps its original moment. */
    public int complete(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE calendar_events SET completed_at = COALESCE(completed_at, :now),
                                              updated_at = CASE WHEN completed_at IS NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now)).update();
    }

    /** Idempotent: reopening a pending item changes nothing. */
    public int reopen(UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                        UPDATE calendar_events SET completed_at = NULL,
                                              updated_at = CASE WHEN completed_at IS NOT NULL THEN :now ELSE updated_at END
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now)).update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM calendar_events WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).update();
    }
}
