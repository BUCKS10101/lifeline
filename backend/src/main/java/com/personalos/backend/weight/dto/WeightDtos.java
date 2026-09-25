package com.personalos.backend.weight.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WeightDtos {

    private WeightDtos() {
    }

    /**
     * The whole reading for the day. {@code PUT} replaces the entry, so an omitted or blank {@code notes} clears
     * any existing note. Weight is in kilograms.
     */
    public record UpsertWeightEntryRequest(
            @NotNull @DecimalMin("20") @DecimalMax("500") @Digits(integer = 3, fraction = 2) BigDecimal weightKg,
            @Size(max = 500) String notes
    ) {}

    public record WeightEntryResponse(LocalDate date, BigDecimal weightKg, String notes) {}

    // ---- series -------------------------------------------------------------------------------

    public enum Granularity { DAILY, WEEKLY, MONTHLY }

    /** A point on a weight chart. The JSON shape depends on the granularity. */
    public sealed interface SeriesPoint permits DailyPoint, BucketPoint {}

    /** One logged day, with the mean of the entries in the 7 calendar days ending on it (a smoothed trend). */
    public record DailyPoint(LocalDate date, BigDecimal weightKg, BigDecimal trendKg) implements SeriesPoint {}

    /** A week (starting Monday) or a calendar month, summarised over the entries inside the requested range. */
    public record BucketPoint(LocalDate periodStart, BigDecimal averageKg, BigDecimal minKg, BigDecimal maxKg, int entries)
            implements SeriesPoint {}

    public record WeightSeries(String granularity, LocalDate from, LocalDate to, List<? extends SeriesPoint> points) {}

    // ---- target -------------------------------------------------------------------------------

    public record UpsertWeightTargetRequest(
            @NotNull @DecimalMin("20") @DecimalMax("500") @Digits(integer = 3, fraction = 2) BigDecimal targetWeightKg
    ) {}

    public record WeightTargetResponse(BigDecimal targetWeightKg, LocalDate startedOn) {}

    // ---- summary ------------------------------------------------------------------------------

    public enum Direction { LOSE, GAIN, MAINTAIN }

    public record WeightPoint(BigDecimal weightKg, LocalDate date) {}

    /** {@code percent} is null when maintaining (there is nothing to travel). */
    public record Progress(Direction direction, BigDecimal percent, BigDecimal remainingKg, boolean reached) {}

    /** How much the weight changed between an earlier entry (the baseline) and the latest one. */
    public record Change(BigDecimal changeKg, BigDecimal baselineKg, LocalDate baselineDate) {}

    public record ChangeWindows(Change last7Days, Change last30Days) {}

    public record WeekAverage(LocalDate periodStart, BigDecimal averageKg, int entries) {}

    public record WeekAverages(WeekAverage thisWeek, WeekAverage lastWeek) {}

    /** Every part is null when it cannot be computed (for example no entries yet, or no target set). */
    public record WeightSummary(WeightPoint current, WeightPoint starting, WeightTargetResponse target, Progress progress,
                                ChangeWindows change, WeekAverages weekAverage, long entryCount) {}
}
