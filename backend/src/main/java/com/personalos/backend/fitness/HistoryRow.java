package com.personalos.backend.fitness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One set together with where it happened. Feeds history, records and personal-record flags. */
public record HistoryRow(UUID exerciseId, UUID workoutId, LocalDate performedOn, Instant startedAt,
                         UUID setId, int setNumber, BigDecimal weightKg, int reps, boolean warmup) {

    public PersonalRecordCalculator.HistorySet toHistorySet() {
        return new PersonalRecordCalculator.HistorySet(setId, weightKg, reps, warmup, performedOn);
    }
}
