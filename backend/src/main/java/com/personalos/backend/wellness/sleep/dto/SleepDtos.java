package com.personalos.backend.wellness.sleep.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SleepDtos {

    private SleepDtos() {
    }

    private static final String CLOCK = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /** Two clock times in the person's own timezone, as {@code HH:mm}. The path date is the day they woke up. */
    public record UpsertSleepEntryRequest(
            @NotNull @Pattern(regexp = CLOCK, message = "must be a time like 23:30") String bedtime,
            @NotNull @Pattern(regexp = CLOCK, message = "must be a time like 07:15") String wakeTime
    ) {}

    /** {@code bedtime} and {@code wakeTime} are wall-clock times in {@code timeZone}, the zone in force when it was logged. */
    public record SleepEntryResponse(LocalDate date, LocalDate bedtimeDate, String bedtime, String wakeTime,
                                     Instant bedtimeAt, Instant wokeAt, String timeZone, int durationMinutes) {}

    public record SleepPoint(LocalDate date, String bedtime, String wakeTime, int durationMinutes) {}

    /** The mean duration over the nights logged in a period, rounded to whole minutes. */
    public record SleepAverage(int durationMinutes, int entries) {}

    /**
     * {@code average} covers the requested range and {@code previousAverage} the equally long period just before it. Each
     * is null when that period has no nights.
     */
    public record SleepSeries(LocalDate from, LocalDate to, List<SleepPoint> points, SleepAverage average, SleepAverage previousAverage) {}
}
