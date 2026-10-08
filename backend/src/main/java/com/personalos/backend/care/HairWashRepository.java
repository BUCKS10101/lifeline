package com.personalos.backend.care;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All SQL for hair-wash entries. Every statement filters by the owner's id. */
@Repository
public class HairWashRepository {

    private final JdbcClient jdbc;

    public HairWashRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID id, LocalDate washDate) {}

    private static Row map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("wash_date", LocalDate.class));
    }

    public Optional<Row> find(UUID userId, UUID id) {
        return jdbc.sql("SELECT id, wash_date FROM hair_wash_entries WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).query(HairWashRepository::map).optional();
    }

    public Optional<LocalDate> latest(UUID userId) {
        return jdbc.sql("SELECT MAX(wash_date) AS m FROM hair_wash_entries WHERE user_id = :userId")
                .param("userId", userId).query((rs, n) -> rs.getObject("m", LocalDate.class)).optional()
                .filter(java.util.Objects::nonNull);
    }

    /** Every entry in a date range, oldest first (for a month's worth of history/calendar rendering). */
    public List<Row> listInRange(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT id, wash_date FROM hair_wash_entries
                        WHERE user_id = :userId AND wash_date BETWEEN :from AND :to
                        ORDER BY wash_date
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query(HairWashRepository::map).list();
    }

    /** Adds the entry unless this date already has one. Returns 1 when added, 0 when the date was taken. */
    public int insertIfAbsent(UUID id, UUID userId, LocalDate date, Instant now) {
        return jdbc.sql("""
                        INSERT INTO hair_wash_entries (id, user_id, wash_date, created_at, updated_at)
                        VALUES (:id, :userId, :date, :now, :now)
                        ON CONFLICT (user_id, wash_date) DO NOTHING
                        """)
                .param("id", id).param("userId", userId).param("date", date).param("now", Timestamp.from(now))
                .update();
    }

    public Optional<UUID> idForDate(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT id FROM hair_wash_entries WHERE user_id = :userId AND wash_date = :date")
                .param("userId", userId).param("date", date).query(UUID.class).optional();
    }

    /** Moves an entry to a different date. Returns rows changed (0 or 1); a collision throws (unique constraint). */
    public int updateDate(UUID id, UUID userId, LocalDate date, Instant now) {
        return jdbc.sql("UPDATE hair_wash_entries SET wash_date = :date, updated_at = :now WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).param("date", date).param("now", Timestamp.from(now))
                .update();
    }

    public int delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM hair_wash_entries WHERE id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).update();
    }
}
