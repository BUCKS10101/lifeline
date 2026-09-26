package com.personalos.backend.wellness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.personalos.backend.wellness.sleep.dto.SleepDtos.SleepEntryResponse;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

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

    // ---- Today ---------------------------------------------------------------------------------

    /** Last night (the night ending today), or null when it has not been logged. Progress is against the optional sleep goal. */
    public record SleepToday(SleepEntryResponse entry, Integer goalMinutes, Integer progressPercent, boolean goalReached) {}

    public record WaterToday(int totalMl, Integer goalMl, Integer progressPercent, boolean goalReached, int entryCount) {}

    public record ProteinToday(int totalG, Integer goalG, Integer progressPercent, boolean goalReached, int entryCount) {}

    /**
     * What the Today view needs. A metric the person has hidden is null. {@code date} is today in their timezone.
     * Goals and progress are only the person's own numbers; nothing here is advice.
     */
    public record TodayResponse(LocalDate date, PreferencesResponse preferences, SleepToday sleep, WaterToday water, ProteinToday protein) {}
}
