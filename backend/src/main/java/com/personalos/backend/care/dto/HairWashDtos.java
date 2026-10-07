package com.personalos.backend.care.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HairWashDtos {

    private HairWashDtos() {
    }

    /** {@code date} is optional: omitted or explicitly null both mean "today" (the person's own today). */
    public record MarkHairWashRequest(LocalDate date) {}

    public record EditHairWashRequest(@NotNull LocalDate date) {}

    public record HairWashEntry(UUID id, LocalDate washDate) {}

    /**
     * {@code lastWashedOn} and {@code daysAgo} cover the whole history, not just {@code month}; both are null when
     * nothing has ever been logged. {@code entries} are only the requested month's, for the history/calendar view.
     */
    public record HairWashSummary(LocalDate lastWashedOn, Integer daysAgo, String month, List<HairWashEntry> entries) {}
}
