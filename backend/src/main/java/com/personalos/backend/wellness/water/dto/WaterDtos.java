package com.personalos.backend.wellness.water.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WaterDtos {

    private WaterDtos() {
    }

    /**
     * One drink in millilitres. {@code date} is optional (default today in the person's timezone) and {@code id} is an
     * optional client-generated UUID: sending the same id again returns the entry that already exists instead of adding
     * a second one, which makes retries safe.
     */
    public record AddWaterRequest(
            @NotNull @Min(10) @Max(5000) Integer amountMl,
            LocalDate date,
            UUID id
    ) {}

    public record WaterEntryResponse(UUID id, LocalDate date, int amountMl, Instant loggedAt) {}

    /** The new (or already existing) drink and that day's total after it. */
    public record WaterAddedResponse(WaterEntryResponse entry, int totalMl) {}

    /** One day: the total, progress toward the optional goal, and the drinks newest first. */
    public record WaterDayResponse(LocalDate date, int totalMl, Integer goalMl, Integer progressPercent, boolean goalReached,
                                   List<WaterEntryResponse> entries) {}
}
