package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.MuscleGroup;

import java.math.BigDecimal;

/** A working set with the muscle group of its exercise. Feeds the summary. */
public record SetVolumeRow(MuscleGroup muscleGroup, BigDecimal weightKg, int reps) {
}
