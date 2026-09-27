package com.personalos.backend.habits;

import java.time.LocalDate;
import java.util.Set;

/**
 * The current and longest streak of a habit. Pure: no database, no clock; today is passed in.
 *
 * <p>A streak is a run of consecutive <em>scheduled</em> days that were done. A day the habit is not scheduled on is
 * simply skipped: it neither breaks nor extends a streak. Today counts specially: if it is scheduled but not yet done,
 * it does not break the streak, because the day is still open. Any other scheduled, undone day breaks it.
 *
 * <p>This makes the current streak exactly the running count at the end of a single forward pass from the habit's
 * start date to today, which is also how the longest streak is found, so one pass computes both.
 */
public final class HabitStreaks {

    private HabitStreaks() {
    }

    public record Result(int current, int longest) {
    }

    /** {@code completedDates} only needs to contain dates on or after {@code startedOn}; earlier ones are ignored. */
    public static Result compute(int scheduleMask, LocalDate startedOn, LocalDate today, Set<LocalDate> completedDates) {
        if (startedOn.isAfter(today)) {
            return new Result(0, 0);
        }
        int running = 0;
        int longest = 0;
        for (LocalDate day = startedOn; !day.isAfter(today); day = day.plusDays(1)) {
            if (!WeekdayMask.isScheduled(scheduleMask, day)) {
                continue; // not scheduled: no effect either way
            }
            if (completedDates.contains(day)) {
                running++;
                longest = Math.max(longest, running);
            } else if (day.equals(today)) {
                // today is still open; not being done yet does not break the streak
            } else {
                running = 0;
            }
        }
        return new Result(running, longest);
    }
}
