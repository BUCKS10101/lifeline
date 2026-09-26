package com.personalos.backend.wellness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class WellnessDtos {

    private WellnessDtos() {
    }

    /** Which metrics are shown, and the optional daily goals. A goal that is null is not set. */
    public record PreferencesResponse(boolean sleepEnabled, boolean waterEnabled, boolean proteinEnabled,
                                      Integer waterGoalMl, Integer proteinGoalG, Integer sleepGoalMinutes) {
        public static PreferencesResponse defaults() {
            return new PreferencesResponse(true, true, true, null, null, null);
        }
    }

    /** {@code PUT} replaces the whole thing, so an omitted or null goal clears it. */
    public record UpdatePreferencesRequest(
            @NotNull Boolean sleepEnabled,
            @NotNull Boolean waterEnabled,
            @NotNull Boolean proteinEnabled,
            @Min(250) @Max(10000) Integer waterGoalMl,
            @Min(10) @Max(500) Integer proteinGoalG,
            @Min(240) @Max(960) Integer sleepGoalMinutes
    ) {}
}
