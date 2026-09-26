package com.personalos.backend.wellness.protein;

import com.personalos.backend.wellness.protein.dto.ProteinDtos.ProteinSuggestion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only derived data: day totals and the person's own frequent labels. Nothing here is stored. */
@Repository
public class ProteinQueries {

    /** Only the most recent entries are considered, so the query cannot grow without bound. */
    static final int SUGGESTION_WINDOW = 1000;
    static final int SUGGESTION_LIMIT = 6;

    private final JdbcClient jdbc;

    public ProteinQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DayTotal(int totalG, int entries) {}

    public DayTotal dayTotal(UUID userId, LocalDate date) {
        return jdbc.sql("SELECT COALESCE(SUM(grams), 0) AS total, COUNT(*) AS entries FROM protein_entries WHERE user_id = :userId AND log_date = :date")
                .param("userId", userId).param("date", date)
                .query((rs, n) -> new DayTotal(rs.getInt("total"), rs.getInt("entries")))
                .single();
    }

    /**
     * The person's own labels, most used first (ties: the one used most recently), each shown as they last typed it
     * with the grams of that last use. Labels that differ only by case are one label. Entries without a label and other
     * people's entries never contribute.
     */
    public List<ProteinSuggestion> suggestions(UUID userId) {
        return jdbc.sql("""
                        WITH recent AS (
                            SELECT lower(label) AS key, label, grams, logged_at, id
                            FROM protein_entries
                            WHERE user_id = :userId AND label IS NOT NULL
                            ORDER BY logged_at DESC, id DESC
                            LIMIT %d),
                        usage AS (SELECT key, COUNT(*) AS uses, MAX(logged_at) AS last_at FROM recent GROUP BY key),
                        latest AS (SELECT DISTINCT ON (key) key, label, grams FROM recent ORDER BY key, logged_at DESC, id DESC)
                        SELECT l.label, l.grams, u.uses
                        FROM usage u JOIN latest l ON l.key = u.key
                        ORDER BY u.uses DESC, u.last_at DESC, l.key
                        LIMIT %d
                        """.formatted(SUGGESTION_WINDOW, SUGGESTION_LIMIT))
                .param("userId", userId)
                .query((rs, n) -> new ProteinSuggestion(rs.getString("label"), rs.getInt("grams"), rs.getInt("uses")))
                .list();
    }
}
