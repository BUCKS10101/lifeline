package com.personalos.backend.wellness;

import com.personalos.backend.wellness.dto.WellnessDtos.PreferencesResponse;
import com.personalos.backend.wellness.dto.WellnessDtos.UpdatePreferencesRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Which metrics a person shows and their optional goals. One row per person, created on first save. */
@Service
public class WellnessPreferencesService {

    private final JdbcClient jdbc;
    private final Clock clock;

    public WellnessPreferencesService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The saved preferences, or the defaults (everything shown, no goals) for someone who never saved any. */
    public PreferencesResponse get(UUID userId) {
        return jdbc.sql("""
                        SELECT sleep_enabled, water_enabled, protein_enabled, water_goal_ml, protein_goal_g, sleep_goal_minutes
                        FROM wellness_preferences WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query((rs, n) -> new PreferencesResponse(rs.getBoolean("sleep_enabled"), rs.getBoolean("water_enabled"),
                        rs.getBoolean("protein_enabled"), (Integer) rs.getObject("water_goal_ml"),
                        (Integer) rs.getObject("protein_goal_g"), (Integer) rs.getObject("sleep_goal_minutes")))
                .optional()
                .orElseGet(PreferencesResponse::defaults);
    }

    /** Saves the preferences atomically. Idempotent: repeating the same request changes nothing. */
    public PreferencesResponse replace(UUID userId, UpdatePreferencesRequest request) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        jdbc.sql("""
                        INSERT INTO wellness_preferences (user_id, sleep_enabled, water_enabled, protein_enabled,
                                                          water_goal_ml, protein_goal_g, sleep_goal_minutes, created_at, updated_at)
                        VALUES (:userId, :sleep, :water, :protein, :waterGoal, :proteinGoal, :sleepGoal, :now, :now)
                        ON CONFLICT (user_id) DO UPDATE SET
                            sleep_enabled = EXCLUDED.sleep_enabled, water_enabled = EXCLUDED.water_enabled,
                            protein_enabled = EXCLUDED.protein_enabled, water_goal_ml = EXCLUDED.water_goal_ml,
                            protein_goal_g = EXCLUDED.protein_goal_g, sleep_goal_minutes = EXCLUDED.sleep_goal_minutes,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("userId", userId).param("sleep", request.sleepEnabled()).param("water", request.waterEnabled())
                .param("protein", request.proteinEnabled()).param("waterGoal", request.waterGoalMl(), java.sql.Types.INTEGER)
                .param("proteinGoal", request.proteinGoalG(), java.sql.Types.INTEGER)
                .param("sleepGoal", request.sleepGoalMinutes(), java.sql.Types.INTEGER).param("now", now)
                .update();
        return get(userId);
    }
}
