package com.personalos.backend.tasks;

import com.personalos.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The four ways of looking at tasks. Today includes what is overdue, so a task never falls out of sight for being late.
 */
public enum TaskView {
    /** Open and due today or earlier. */
    TODAY,
    /** Open and due after today. */
    UPCOMING,
    /** Open with no date. */
    ANYTIME,
    /** Completed, newest first. */
    DONE;

    /** Case-insensitive; a missing value means today. */
    public static TaskView parse(String value) {
        if (value == null) return TODAY;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VIEW", "view must be today, upcoming, anytime or done");
        }
    }
}
