package com.personalos.backend.goals;

/**
 * How far a goal's linked tasks have gone. Pure: no database, no clock. Progress comes from tasks only; habits are
 * supporting work and never change the percentage.
 */
public final class GoalProgress {

    private GoalProgress() {
    }

    /** Null when the goal has no linked tasks: there is nothing to measure yet. Otherwise 0 to 100, rounded. */
    public static Integer percent(int taskCount, int doneCount) {
        if (taskCount <= 0) return null;
        return (int) Math.round(doneCount * 100.0 / taskCount);
    }
}
