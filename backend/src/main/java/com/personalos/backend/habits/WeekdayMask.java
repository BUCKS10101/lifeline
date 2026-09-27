package com.personalos.backend.habits;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Which days of the week a habit applies to, stored as one small bitmask (bit 0 is Monday, bit 6 is Sunday; 127 is
 * every day). The API and the rest of the code speak in ISO weekday numbers (1 = Monday to 7 = Sunday); this is the
 * only place the two are converted. Pure: no database, no clock.
 */
public final class WeekdayMask {

    public static final int EVERY_DAY = 0b1111111;

    private WeekdayMask() {
    }

    public static boolean isScheduled(int mask, LocalDate date) {
        return (mask & bitFor(date.getDayOfWeek().getValue())) != 0;
    }

    /** Rejects anything outside 1 to 7. Duplicates are the caller's problem to reject (a mask cannot express them). */
    public static int fromWeekdays(Collection<Integer> isoWeekdays) {
        int mask = 0;
        for (int day : isoWeekdays) {
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("weekday must be 1 (Monday) to 7 (Sunday)");
            }
            mask |= bitFor(day);
        }
        return mask;
    }

    /** True when the list has a duplicate once out-of-range values are ruled out separately. */
    public static boolean hasDuplicates(Collection<Integer> isoWeekdays) {
        return new LinkedHashSet<>(isoWeekdays).size() != isoWeekdays.size();
    }

    /** ISO weekday numbers in the mask, ascending. */
    public static List<Integer> toWeekdays(int mask) {
        List<Integer> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            if ((mask & bitFor(day)) != 0) days.add(day);
        }
        return days;
    }

    private static int bitFor(int isoWeekday) {
        return 1 << (isoWeekday - 1);
    }
}
