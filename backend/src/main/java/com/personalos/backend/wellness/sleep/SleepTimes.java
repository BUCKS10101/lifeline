package com.personalos.backend.wellness.sleep;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the two clock times a person types into the two moments they mean. Pure: no database, no clock.
 *
 * <p>The wake time is on the wake date. The bedtime is the <em>latest moment before waking</em> that shows the bedtime
 * clock time, so 23:30 to 07:15 is the previous evening, 01:00 to 08:00 is the same morning, and 14:00 to 08:00 is the
 * previous afternoon. Within the 24 hours before waking there is exactly one such moment, so nothing is guessed.
 *
 * <p>The only thing rejected is an implausible duration: under {@link #MIN} or over {@link #MAX}. The duration is the
 * real elapsed time between the two moments, so a night across a daylight-saving change is an hour shorter or longer.
 *
 * <p>Two rare clock quirks are resolved the same way every time. A clock time that does not exist (skipped by the clocks
 * going forward) is read as the moment the clocks jump to. A clock time that happens twice (repeated when the clocks go
 * back) means the first time for waking, and the latest time before waking for bedtime, as the rule says.
 */
public final class SleepTimes {

    public static final Duration MIN = Duration.ofMinutes(15);
    public static final Duration MAX = Duration.ofHours(20);

    private SleepTimes() {
    }

    public record Resolved(Instant bedtimeAt, Instant wokeAt, int durationMinutes) {
    }

    /** The times cannot describe a plausible night. The message is safe to show to the user. */
    public static class InvalidSleepException extends RuntimeException {
        public InvalidSleepException(String message) {
            super(message);
        }
    }

    public static Resolved resolve(LocalDate wakeDate, LocalTime bedtime, LocalTime wakeTime, ZoneId zone) {
        if (bedtime.equals(wakeTime)) {
            throw new InvalidSleepException("Bedtime and wake time are the same. Check the times.");
        }
        Instant woke = instants(wakeDate.atTime(wakeTime), zone).get(0); // the first time the clock shows it
        Instant bed = latestBefore(woke, bedtime, wakeDate, zone);

        Duration slept = Duration.between(bed, woke);
        if (slept.compareTo(MIN) < 0) {
            throw new InvalidSleepException("That is shorter than 15 minutes. Check the times.");
        }
        if (slept.compareTo(MAX) > 0) {
            throw new InvalidSleepException("That is longer than 20 hours. Check the times.");
        }
        return new Resolved(bed, woke, (int) slept.toMinutes());
    }

    /** The latest moment strictly before {@code woke} that shows {@code clock}, looking on the wake date and the day before. */
    private static Instant latestBefore(Instant woke, LocalTime clock, LocalDate wakeDate, ZoneId zone) {
        Instant best = null;
        for (LocalDate day : List.of(wakeDate.minusDays(1), wakeDate)) {
            for (Instant candidate : instants(day.atTime(clock), zone)) {
                if (candidate.isBefore(woke) && (best == null || candidate.isAfter(best))) best = candidate;
            }
        }
        return best; // the previous day always has a candidate before waking
    }

    /** Every moment a local date-time refers to, earliest first: two when the clocks repeat it, one normally, one (shifted forward) when skipped. */
    private static List<Instant> instants(LocalDateTime local, ZoneId zone) {
        List<Instant> result = new ArrayList<>();
        for (ZoneOffset offset : zone.getRules().getValidOffsets(local)) result.add(local.toInstant(offset));
        if (result.isEmpty()) result.add(ZonedDateTime.ofLocal(local, zone, null).toInstant());
        result.sort(Instant::compareTo);
        return result;
    }
}
