package com.personalos.backend.weight.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
