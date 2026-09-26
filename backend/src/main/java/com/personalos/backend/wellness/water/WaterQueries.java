package com.personalos.backend.wellness.water;

import com.personalos.backend.wellness.dto.WellnessDtos.IntakeAverage;
import com.personalos.backend.wellness.water.dto.WaterDtos.WaterPoint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only derived totals. The total is never stored: it is summed from the entries. */
@Repository
public class WaterQueries {

    private final JdbcClient jdbc;

    public WaterQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DayTotal(int totalMl, int entries) {}

    public DayTotal dayTotal(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT COALESCE(SUM(amount_ml), 0) AS total, COUNT(*) AS entries FROM water_entries WHERE user_id = :userId AND log_date = :date")
                .param("userId", userId).param("date", date)
                .query((rs, n) -> new DayTotal(rs.getInt("total"), rs.getInt("entries")))
                .single();
    }

    /** Every day from {@code from} to {@code to}, oldest first, with days that have no entry listed as 0. */
    public List<WaterPoint> series(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT d.day::date AS day, COALESCE(SUM(e.amount_ml), 0) AS total
                        FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d(day)
                        LEFT JOIN water_entries e ON e.user_id = :userId AND e.log_date = d.day::date
                        GROUP BY d.day ORDER BY d.day
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> new WaterPoint(rs.getObject("day", LocalDate.class), rs.getInt("total")))
                .list();
    }

    /** The mean daily total (whole units) over the days in the range that have at least one entry, or empty when none do. */
    public Optional<IntakeAverage> average(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS days, ROUND(AVG(total))::int AS mean
                        FROM (SELECT SUM(amount_ml) AS total FROM water_entries
                              WHERE user_id = :userId AND log_date BETWEEN :from AND :to GROUP BY log_date) t
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> rs.getInt("days") == 0 ? null : new IntakeAverage(rs.getInt("mean"), rs.getInt("days")))
                .optional();
    }
}
