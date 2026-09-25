package com.personalos.backend.weight;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.weight.dto.WeightDtos.UpsertWeightTargetRequest;
import com.personalos.backend.weight.dto.WeightDtos.WeightTargetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** One target per user. */
@Service
public class WeightTargetService {

    private final JdbcClient jdbc;
    private final ProfileService profiles;
    private final Clock clock;

    public WeightTargetService(JdbcClient jdbc, ProfileService profiles, Clock clock) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.clock = clock;
    }

    public Optional<WeightTargetResponse> find(UUID userId) {
        return jdbc.sql("SELECT target_weight_kg, started_on FROM weight_targets WHERE user_id = :userId")
                .param("userId", userId)
                .query((rs, n) -> new WeightTargetResponse(rs.getBigDecimal("target_weight_kg"), rs.getObject("started_on", LocalDate.class)))
                .optional();
    }

    public WeightTargetResponse get(UUID userId) {
        return find(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No weight target set"));
    }

    /**
     * Sets the target, atomically. Idempotent: repeating the same value changes nothing, including the start date.
     * A different value starts a new goal, so the start date moves to today (in the user's timezone).
     */
    public WeightTargetResponse upsert(UUID userId, UpsertWeightTargetRequest request) {
        BigDecimal target = request.targetWeightKg().setScale(2);
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, profiles.timezoneOf(userId));
        return jdbc.sql("""
                        INSERT INTO weight_targets (user_id, target_weight_kg, started_on, created_at, updated_at)
                        VALUES (:userId, :target, :today, :now, :now)
                        ON CONFLICT (user_id) DO UPDATE SET
                            started_on = CASE WHEN weight_targets.target_weight_kg = EXCLUDED.target_weight_kg
                                              THEN weight_targets.started_on ELSE EXCLUDED.started_on END,
                            updated_at = CASE WHEN weight_targets.target_weight_kg = EXCLUDED.target_weight_kg
                                              THEN weight_targets.updated_at ELSE EXCLUDED.updated_at END,
                            target_weight_kg = EXCLUDED.target_weight_kg
                        RETURNING target_weight_kg, started_on
                        """)
                .param("userId", userId).param("target", target).param("today", today)
                .param("now", java.sql.Timestamp.from(now))
                .query((rs, n) -> new WeightTargetResponse(rs.getBigDecimal("target_weight_kg"), rs.getObject("started_on", LocalDate.class)))
                .single();
    }

    public void delete(UUID userId) {
        if (jdbc.sql("DELETE FROM weight_targets WHERE user_id = :userId").param("userId", userId).update() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No weight target set");
        }
    }
}
