package com.personalos.backend.goals;

import com.personalos.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Where a goal stands. Independent of its task progress: a person can mark a goal achieved at any percentage. */
public enum GoalStatus {
    ACTIVE, ACHIEVED, ARCHIVED;

    /** Case-insensitive; a missing value means active. */
    public static GoalStatus parse(String value) {
        if (value == null) return ACTIVE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status must be active, achieved or archived");
        }
    }
}
