package com.personalos.backend.calendar;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All SQL for calendar events. Every statement filters by the owner's id. {@code listBetween} takes a padded instant
 * window; the service turns each row's own stored zone into its precise local day range and filters again there, so
 * no timezone arithmetic happens in SQL.
 */
@Repository
public class CalendarEventRepository {

    private static final String SELECT = "SELECT id, title, description, start_at, end_at, zone_id, all_day FROM calendar_events";

    private final JdbcClient jdbc;

    public CalendarEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID id, String title, String description, Instant startAt, Instant endAt, String zoneId, boolean allDay) {}

    private static Row map(ResultSet rs, int n) throws SQLException {
        Timestamp end = rs.getTimestamp("end_at");
        return new Row(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getTimestamp("start_at").toInstant(), end == null ? null : end.toInstant(),
                rs.getString("zone_id"), rs.getBoolean("all_day"));
    }

    public Optional<Row> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).query(CalendarEventRepository::map).optional();
    }

    /** Every event that could possibly touch the padded window; the service narrows this to the exact local days. */
    public List<Row> listBetween(UUID userId, Instant from, Instant to) {
        return jdbc.sql(SELECT + " WHERE user_id = :userId AND start_at < :to AND (end_at IS NULL OR end_at >= :from) ORDER BY start_at")
                .param("userId", userId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query(CalendarEventRepository::map).list();
    }

    public UUID insert(UUID userId, String title, String description, Instant startAt, Instant endAt, String zoneId, boolean allDay, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO calendar_events (id, user_id, title, description, start_at, end_at, zone_id, all_day, created_at, updated_at)
                        VALUES (:id, :userId, :title, :description, :startAt, :endAt, :zoneId, :allDay, :now, :now)
                        """)
                .param("id", id).param("userId", userId).param("title", title)
                .param("description", description, Types.VARCHAR).param("startAt", Timestamp.from(startAt))
                .param("endAt", endAt == null ? null : Timestamp.from(endAt), Types.TIMESTAMP)
                .param("zoneId", zoneId).param("allDay", allDay).param("now", Timestamp.from(now))
                .update();
        return id;
    }

    /** Replaces every editable field (the zone never changes after creation). Returns rows changed (0 or 1). */
    public int update(UUID id, UUID userId, String title, String description, Instant startAt, Instant endAt, boolean allDay, Instant now) {
        return jdbc.sql("""
                        UPDATE calendar_events SET title = :title, description = :description, start_at = :startAt,
                                                    end_at = :endAt, all_day = :allDay, updated_at = :now
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id).param("userId", userId).param("title", title)
                .param("description", description, Types.VARCHAR).param("startAt", Timestamp.from(startAt))
                .param("endAt", endAt == null ? null : Timestamp.from(endAt), Types.TIMESTAMP)
                .param("allDay", allDay).param("now", Timestamp.from(now))
                .update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM calendar_events WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).update();
    }
}
