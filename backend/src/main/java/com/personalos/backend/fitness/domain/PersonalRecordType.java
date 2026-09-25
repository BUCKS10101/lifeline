package com.personalos.backend.fitness.domain;

/** Kinds of personal record. Derived at read time from set history; never stored. */
public enum PersonalRecordType {
    WEIGHT, ESTIMATED_1RM, REPS_AT_WEIGHT
}
