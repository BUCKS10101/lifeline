package com.personalos.backend.wellness.protein.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProteinDtos {

    private ProteinDtos() {
    }

    /**
     * Protein in whole grams with an optional short label such as "Whey shake". A blank label is the same as none.
     * {@code date} is optional (default today) and {@code id} is an optional client-generated UUID that makes a retry safe.
     */
    public record AddProteinRequest(
            @NotNull @Min(1) @Max(500) Integer grams,
            @Size(max = 60) String label,
            LocalDate date,
            UUID id
    ) {}

    public record ProteinEntryResponse(UUID id, LocalDate date, int grams, String label, Instant loggedAt) {}

    /** The new (or already existing) entry and that day's total after it. */
    public record ProteinAddedResponse(ProteinEntryResponse entry, int totalG) {}

    /** One day: the total, progress toward the optional goal, and the entries newest first. */
    public record ProteinDayResponse(LocalDate date, int totalG, Integer goalG, Integer progressPercent, boolean goalReached,
                                     List<ProteinEntryResponse> entries) {}

    /** One of the person's own past labels, with the grams they logged with it last and how often they used it. */
    public record ProteinSuggestion(String label, int grams, int uses) {}
}
