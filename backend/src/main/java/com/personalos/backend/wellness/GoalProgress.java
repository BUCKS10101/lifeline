package com.personalos.backend.wellness;

/**
 * Progress toward an optional daily goal. Pure: no database, no clock. There is no verdict on the amount, only how far
 * along it is: the percentage stops at 100, and "reached" is simply the total being at or above the goal.
 */
public record GoalProgress(Integer percent, boolean reached) {

    public static final GoalProgress NONE = new GoalProgress(null, false);

    /** {@code goal} is null when the person has not set one. */
    public static GoalProgress of(long total, Integer goal) {
        if (goal == null || goal <= 0) return NONE;
        long percent = Math.min(100, Math.max(0, total) * 100 / goal);
        return new GoalProgress((int) percent, total >= goal);
    }
}
