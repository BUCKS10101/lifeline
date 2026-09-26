package com.personalos.backend.wellness.water;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
