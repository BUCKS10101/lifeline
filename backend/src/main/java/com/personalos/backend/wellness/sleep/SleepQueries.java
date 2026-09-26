package com.personalos.backend.wellness.sleep;

import com.personalos.backend.wellness.sleep.dto.SleepDtos.SleepAverage;
import com.personalos.backend.wellness.sleep.dto.SleepDtos.SleepPoint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only derived-data queries. The averages are worked out in the database from the stored moments. */
@Repository
public class SleepQueries {

    private final JdbcClient jdbc;

    public SleepQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every night in the inclusive range, oldest first, shown in the zone each was logged in. */
    public List<SleepPoint> points(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT sleep_date, bedtime_at, woke_at, zone_id,
                               (EXTRACT(EPOCH FROM (woke_at - bedtime_at)) / 60)::int AS minutes
                        FROM sleep_entries
                        WHERE user_id = :userId AND sleep_date BETWEEN :from AND :to
                        ORDER BY sleep_date
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> {
                    ZoneId zone = ZoneId.of(rs.getString("zone_id"));
                    return new SleepPoint(rs.getObject("sleep_date", LocalDate.class),
                            SleepEntryMapper.clock(rs.getTimestamp("bedtime_at").toInstant(), zone),
                            SleepEntryMapper.clock(rs.getTimestamp("woke_at").toInstant(), zone), rs.getInt("minutes"));
                })
                .list();
    }

    /** The mean duration in whole minutes and the number of nights, or empty when there are none. */
    public Optional<SleepAverage> average(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS nights,
                               ROUND(AVG(EXTRACT(EPOCH FROM (woke_at - bedtime_at)) / 60))::int AS minutes
                        FROM sleep_entries
                        WHERE user_id = :userId AND sleep_date BETWEEN :from AND :to
                        """)
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> rs.getInt("nights") == 0 ? null : new SleepAverage(rs.getInt("minutes"), rs.getInt("nights")))
                .optional();
    }
}
