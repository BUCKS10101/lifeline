package com.personalos.backend.weight;

import com.personalos.backend.weight.dto.WeightDtos.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only derived-data queries. Nothing here is stored: it is all computed from the entries on request. */
@Repository
public class WeightAnalyticsQueries {

    private final JdbcClient jdbc;

    public WeightAnalyticsQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Each logged day in range, with the mean of the entries in the 7 calendar days ending on it. The window is
     * computed over all of the user's entries and filtered afterwards, so the first point in range still sees the
     * days before it.
     */
    public List<DailyPoint> daily(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT entry_date, weight_kg, trend_kg FROM (
                            SELECT entry_date, weight_kg,
                                   ROUND(AVG(weight_kg) OVER (ORDER BY entry_date
                                         RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW), 2) AS trend_kg
                            FROM weight_entries WHERE user_id = :userId) t
                        WHERE entry_date BETWEEN :from AND :to
                        ORDER BY entry_date
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> new DailyPoint(rs.getObject("entry_date", LocalDate.class),
                        rs.getBigDecimal("weight_kg"), rs.getBigDecimal("trend_kg")))
                .list();
    }

    /** Buckets by ISO week (Monday start) or calendar month; {@code unit} is only ever 'week' or 'month'. */
    public List<BucketPoint> buckets(UUID userId, LocalDate from, LocalDate to, String unit) {
        return jdbc.sql("""
                        SELECT date_trunc('%s', entry_date::timestamp)::date AS period_start,
                               ROUND(AVG(weight_kg), 2) AS avg_kg, MIN(weight_kg) AS min_kg, MAX(weight_kg) AS max_kg,
                               COUNT(*) AS entries
                        FROM weight_entries
                        WHERE user_id = :userId AND entry_date BETWEEN :from AND :to
                        GROUP BY 1 ORDER BY 1
                        """.formatted(unit))
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> new BucketPoint(rs.getObject("period_start", LocalDate.class), rs.getBigDecimal("avg_kg"),
                        rs.getBigDecimal("min_kg"), rs.getBigDecimal("max_kg"), rs.getInt("entries")))
                .list();
    }

    public Optional<WeightPoint> latest(UUID userId) {
        return point("SELECT weight_kg, entry_date FROM weight_entries WHERE user_id = :userId ORDER BY entry_date DESC LIMIT 1", userId, null);
    }

    /** The most recent entry on or before the date. */
    public Optional<WeightPoint> onOrBefore(UUID userId, LocalDate date) {
        return point("SELECT weight_kg, entry_date FROM weight_entries WHERE user_id = :userId AND entry_date <= :d ORDER BY entry_date DESC LIMIT 1", userId, date);
    }

    /** The earliest entry on or after the date. */
    public Optional<WeightPoint> onOrAfter(UUID userId, LocalDate date) {
        return point("SELECT weight_kg, entry_date FROM weight_entries WHERE user_id = :userId AND entry_date >= :d ORDER BY entry_date ASC LIMIT 1", userId, date);
    }

    public long count(UUID userId) {
        return jdbc.sql("SELECT COUNT(*) FROM weight_entries WHERE user_id = :userId").param("userId", userId)
                .query(Long.class).single();
    }

    /** Average and entry count for entries in [from, to]; empty when there are none. */
    public Optional<WeekAverage> average(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT ROUND(AVG(weight_kg), 2) AS avg_kg, COUNT(*) AS entries FROM weight_entries
                        WHERE user_id = :userId AND entry_date BETWEEN :from AND :to
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> rs.getInt("entries") == 0 ? null : new WeekAverage(from, rs.getBigDecimal("avg_kg"), rs.getInt("entries")))
                .optional();
    }

    private Optional<WeightPoint> point(String sql, UUID userId, LocalDate date) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("userId", userId);
        if (date != null) {
            spec = spec.param("d", date);
        }
        return spec.query((rs, n) -> new WeightPoint(rs.getBigDecimal("weight_kg"), rs.getObject("entry_date", LocalDate.class))).optional();
    }
}
