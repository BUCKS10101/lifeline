package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.MuscleGroup;
import com.personalos.backend.fitness.dto.AnalyticsDtos.ProgressionPoint;
import com.personalos.backend.fitness.dto.AnalyticsDtos.TopSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregation over the existing fitness tables. It counts only working sets (not warm-ups) of COMPLETED
 * workouts, grouped by the stored {@code performed_on} date, and adds no tables of its own.
 */
@Repository
public class FitnessAnalyticsQueries {

    private final JdbcClient jdbc;

    public FitnessAnalyticsQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A bucket of the requested range, including buckets with no workouts. */
    public record Bucket(LocalDate periodStart, int workouts) {}

    /** Working-set totals for one muscle group in one bucket. */
    public record BucketMuscle(LocalDate periodStart, MuscleGroup muscleGroup, BigDecimal volumeKg, int sets) {}

    /**
     * Every bucket from the one containing {@code from} to the one containing {@code to}, zero-filled, with the number
     * of completed workouts in the range that fall in it. {@code unit} is only ever 'week' or 'month'.
     */
    public List<Bucket> buckets(UUID userId, LocalDate from, LocalDate to, String unit) {
        return jdbc.sql("""
                        SELECT b.period_start::date AS period_start, COUNT(w.id) AS workouts
                        FROM generate_series(date_trunc('%1$s', :from::timestamp), date_trunc('%1$s', :to::timestamp),
                                             interval '1 %1$s') AS b(period_start)
                        LEFT JOIN workouts w ON w.user_id = :userId AND w.status = 'COMPLETED'
                             AND w.performed_on BETWEEN :from AND :to
                             AND date_trunc('%1$s', w.performed_on::timestamp) = b.period_start
                        GROUP BY b.period_start ORDER BY b.period_start
                        """.formatted(unit))
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> new Bucket(rs.getObject("period_start", LocalDate.class), rs.getInt("workouts")))
                .list();
    }

    public List<BucketMuscle> bucketMuscles(UUID userId, LocalDate from, LocalDate to, String unit) {
        return jdbc.sql("""
                        SELECT date_trunc('%s', w.performed_on::timestamp)::date AS period_start,
                               e.primary_muscle_group AS muscle, SUM(s.weight_kg * s.reps) AS volume, COUNT(*) AS sets
                        FROM workout_sets s
                        JOIN workout_exercises we ON we.id = s.workout_exercise_id
                        JOIN workouts w ON w.id = we.workout_id
                        JOIN exercises e ON e.id = we.exercise_id
                        WHERE w.user_id = :userId AND w.status = 'COMPLETED' AND s.is_warmup = FALSE
                          AND w.performed_on BETWEEN :from AND :to
                        GROUP BY 1, 2
                        """.formatted(unit))
                .param("userId", userId).param("from", from).param("to", to)
                .query((rs, n) -> new BucketMuscle(rs.getObject("period_start", LocalDate.class),
                        MuscleGroup.valueOf(rs.getString("muscle")), rs.getBigDecimal("volume"), rs.getInt("sets")))
                .list();
    }

    /**
     * One point per completed session of the exercise that has at least one working set, oldest first. The top set is
     * the heaviest working set, and among equal weights the one with the most reps. The best estimate is the highest
     * Epley 1RM (weight x (30 + reps) / 30, and just the weight for a single rep), rounded to 2 decimals.
     */
    public List<ProgressionPoint> progression(UUID userId, UUID exerciseId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT DISTINCT ON (w.id) w.id AS workout_id, w.performed_on, w.started_at,
                               s.weight_kg AS top_weight, s.reps AS top_reps,
                               MAX(CASE WHEN s.reps = 1 THEN s.weight_kg ELSE s.weight_kg * (30 + s.reps) / 30.0 END) OVER (PARTITION BY w.id) AS best_1rm,
                               SUM(s.weight_kg * s.reps) OVER (PARTITION BY w.id) AS volume,
                               COUNT(*) OVER (PARTITION BY w.id) AS working_sets
                        FROM workout_sets s
                        JOIN workout_exercises we ON we.id = s.workout_exercise_id
                        JOIN workouts w ON w.id = we.workout_id
                        WHERE w.user_id = :userId AND we.exercise_id = :exerciseId AND w.status = 'COMPLETED'
                          AND s.is_warmup = FALSE AND w.performed_on BETWEEN :from AND :to
                        ORDER BY w.id, s.weight_kg DESC, s.reps DESC, s.set_number
                        """)
                .param("userId", userId).param("exerciseId", exerciseId).param("from", from).param("to", to)
                .query((rs, n) -> new Row(rs.getObject("workout_id", UUID.class), rs.getObject("performed_on", LocalDate.class),
                        rs.getTimestamp("started_at").toInstant(), new TopSet(rs.getBigDecimal("top_weight"), rs.getInt("top_reps")),
                        rs.getBigDecimal("best_1rm").setScale(2, java.math.RoundingMode.HALF_UP),
                        rs.getBigDecimal("volume"), rs.getInt("working_sets")))
                .list().stream()
                .sorted(java.util.Comparator.comparing(Row::performedOn).thenComparing(Row::startedAt).thenComparing(Row::workoutId))
                .map(r -> new ProgressionPoint(r.workoutId(), r.performedOn(), r.topSet(), r.best1rm(), r.volume(), r.sets()))
                .toList();
    }

    private record Row(UUID workoutId, LocalDate performedOn, java.time.Instant startedAt, TopSet topSet,
                       BigDecimal best1rm, BigDecimal volume, int sets) {}

    /** Every completed set (warm-ups included, so the calculator sees what Phase 3 sees) of one exercise, or of all. */
    public List<NamedHistoryRow> history(UUID userId, UUID exerciseId) {
        return jdbc.sql("""
                        SELECT we.exercise_id, e.name, w.id AS workout_id, w.performed_on, w.started_at,
                               s.id AS set_id, s.set_number, s.weight_kg, s.reps, s.is_warmup
                        FROM workout_sets s
                        JOIN workout_exercises we ON we.id = s.workout_exercise_id
                        JOIN workouts w ON w.id = we.workout_id
                        JOIN exercises e ON e.id = we.exercise_id
                        WHERE w.user_id = :userId AND w.status = 'COMPLETED'
                          AND (CAST(:exerciseId AS uuid) IS NULL OR we.exercise_id = CAST(:exerciseId AS uuid))
                        ORDER BY we.exercise_id, w.started_at, w.id, s.set_number
                        """)
                .param("userId", userId).param("exerciseId", exerciseId, java.sql.Types.OTHER)
                .query((rs, n) -> new NamedHistoryRow(rs.getString("name"), new HistoryRow(
                        rs.getObject("exercise_id", UUID.class), rs.getObject("workout_id", UUID.class),
                        rs.getObject("performed_on", LocalDate.class), rs.getTimestamp("started_at").toInstant(),
                        rs.getObject("set_id", UUID.class), rs.getInt("set_number"), rs.getBigDecimal("weight_kg"),
                        rs.getInt("reps"), rs.getBoolean("is_warmup"))))
                .list();
    }

    public record NamedHistoryRow(String exerciseName, HistoryRow row) {}
}
