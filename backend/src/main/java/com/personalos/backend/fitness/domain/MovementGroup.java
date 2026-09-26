package com.personalos.backend.fitness.domain;

/**
 * The four coarse groups training volume is charted by. There is no stored classification: every exercise already has a
 * {@link MuscleGroup}, and this is the single place that maps it to a movement group.
 */
public enum MovementGroup {
    PUSH, PULL, LEGS, CORE_FULL_BODY;

    public static MovementGroup of(MuscleGroup muscle) {
        return switch (muscle) {
            case CHEST, SHOULDERS, TRICEPS -> PUSH;
            case BACK, BICEPS, FOREARMS -> PULL;
            case QUADS, HAMSTRINGS, GLUTES, CALVES -> LEGS;
            case CORE, FULL_BODY -> CORE_FULL_BODY;
        };
    }
}
