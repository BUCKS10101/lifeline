package com.personalos.backend.wellness.sleep;

import com.personalos.backend.wellness.sleep.SleepTimes.InvalidSleepException;
import com.personalos.backend.wellness.sleep.SleepTimes.Resolved;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SleepTimesTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);

    private static Resolved resolve(LocalDate wakeDate, String bed, String wake, ZoneId zone) {
        return SleepTimes.resolve(wakeDate, LocalTime.parse(bed), LocalTime.parse(wake), zone);
    }

    // ---- which side of midnight the bedtime is on ---------------------------------------------

    @Test
    void anEveningBedtimeIsThePreviousEvening() {
        Resolved r = resolve(DAY, "23:30", "07:15", UTC);
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-09-23T23:30:00Z"));
        assertThat(r.wokeAt()).isEqualTo(Instant.parse("2026-09-24T07:15:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(465);
    }

    @Test
    void aBedtimeAfterMidnightIsTheSameMorning() {
        Resolved r = resolve(DAY, "01:00", "08:00", UTC);
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-09-24T01:00:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(420);
    }

    @Test
    void anAfternoonBedtimeIsTheAfternoonBeforeAndIsAccepted() {
        Resolved r = resolve(DAY, "14:00", "08:00", UTC);
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-09-23T14:00:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(18 * 60);
    }

    @Test
    void justBeforeMidnightAndJustAfterAreBothReadCorrectly() {
        assertThat(resolve(DAY, "23:59", "06:00", UTC).durationMinutes()).isEqualTo(361);
        assertThat(resolve(DAY, "00:01", "06:00", UTC).durationMinutes()).isEqualTo(359);
        assertThat(resolve(DAY, "00:00", "07:00", UTC).bedtimeAt()).isEqualTo(Instant.parse("2026-09-24T00:00:00Z"));
    }

    @Test
    void theTimesAreTheOnesOnTheClocksOfThePersonsZone() {
        Resolved r = resolve(DAY, "23:30", "07:15", ZoneId.of("Asia/Kolkata"));
        assertThat(r.wokeAt()).isEqualTo(Instant.parse("2026-09-24T01:45:00Z"));   // 07:15 at +05:30
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-09-23T18:00:00Z")); // 23:30 at +05:30
        assertThat(r.durationMinutes()).isEqualTo(465);
    }

    // ---- the plausibility limits --------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"12:00, 08:00, 1200", "07:45, 08:00, 15", "22:00, 06:00, 480"})
    void durationsInsideTheLimitsAreAccepted(String bed, String wake, int minutes) {
        assertThat(resolve(DAY, bed, wake, UTC).durationMinutes()).isEqualTo(minutes);
    }

    @Test
    void moreThanTwentyHoursIsRejectedAsTooLong() {
        assertThatThrownBy(() -> resolve(DAY, "11:59", "08:00", UTC))
                .isInstanceOf(InvalidSleepException.class).hasMessageContaining("longer than 20 hours");
    }

    @Test
    void aMistypedAmPmTypicallyOverflowsTheLimitAndIsCaught() {
        // 08:00 to 07:00 read literally is 23 hours.
        assertThatThrownBy(() -> resolve(DAY, "08:00", "07:00", UTC))
                .isInstanceOf(InvalidSleepException.class).hasMessageContaining("longer than 20 hours");
    }

    @Test
    void lessThanFifteenMinutesIsRejectedAsTooShort() {
        assertThatThrownBy(() -> resolve(DAY, "07:46", "08:00", UTC))
                .isInstanceOf(InvalidSleepException.class).hasMessageContaining("shorter than 15 minutes");
    }

    @Test
    void identicalTimesAreRejectedWithTheirOwnMessage() {
        assertThatThrownBy(() -> resolve(DAY, "07:00", "07:00", UTC))
                .isInstanceOf(InvalidSleepException.class).hasMessageContaining("same");
    }

    // ---- daylight-saving changes --------------------------------------------------------------

    @Test
    void theNightTheClocksGoForwardIsAnHourShorter() {
        // London, Sunday 29 March 2026: 01:00 GMT became 02:00 BST. Eight hours on the clocks, seven really slept.
        Resolved london = resolve(LocalDate.of(2026, 3, 29), "23:00", "07:00", LONDON);
        assertThat(london.bedtimeAt()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        assertThat(london.wokeAt()).isEqualTo(Instant.parse("2026-03-29T06:00:00Z"));
        assertThat(london.durationMinutes()).isEqualTo(7 * 60);
        // New York, Sunday 8 March 2026.
        assertThat(resolve(LocalDate.of(2026, 3, 8), "23:00", "07:00", NEW_YORK).durationMinutes()).isEqualTo(7 * 60);
    }

    @Test
    void theNightTheClocksGoBackIsAnHourLonger() {
        Resolved london = resolve(LocalDate.of(2026, 10, 25), "23:00", "07:00", LONDON);
        assertThat(london.bedtimeAt()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z")); // 23:00 BST
        assertThat(london.durationMinutes()).isEqualTo(9 * 60);
        assertThat(resolve(LocalDate.of(2026, 11, 1), "23:00", "07:00", NEW_YORK).durationMinutes()).isEqualTo(9 * 60);
    }

    @Test
    void aBedtimeThatHappensTwiceIsTheLatestBeforeWaking() {
        // London, 25 October 2026: 01:30 happens at 00:30Z (BST) and again at 01:30Z (GMT). The latest before waking wins.
        Resolved r = resolve(LocalDate.of(2026, 10, 25), "01:30", "08:00", LONDON);
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(6 * 60 + 30);
    }

    @Test
    void aWakeTimeThatHappensTwiceIsTheFirstTimeTheClockShowsIt() {
        Resolved r = resolve(LocalDate.of(2026, 10, 25), "22:00", "01:30", LONDON);
        assertThat(r.wokeAt()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(3 * 60 + 30);
    }

    @Test
    void aClockTimeThatDoesNotExistIsReadAsTheMomentTheClocksJumpTo() {
        // London, 29 March 2026: 01:30 never happened. It is read as 02:30 BST = 01:30Z.
        Resolved r = resolve(LocalDate.of(2026, 3, 29), "01:30", "08:00", LONDON);
        assertThat(r.bedtimeAt()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        assertThat(r.durationMinutes()).isEqualTo(5 * 60 + 30);
    }

    @Test
    void theDurationIsAlwaysTheElapsedTimeBetweenTheTwoMoments() {
        for (ZoneId zone : new ZoneId[]{UTC, LONDON, NEW_YORK, ZoneId.of("Australia/Sydney"), ZoneId.of("Asia/Kolkata")}) {
            for (LocalDate day : new LocalDate[]{LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 4, 5), DAY}) {
                for (String bed : new String[]{"22:00", "23:59", "00:30", "03:00"}) {
                    Resolved r;
                    try {
                        r = resolve(day, bed, "08:00", zone);
                    } catch (InvalidSleepException ignored) {
                        continue;
                    }
                    assertThat(r.durationMinutes()).isEqualTo((int) Duration.between(r.bedtimeAt(), r.wokeAt()).toMinutes());
                    assertThat(r.durationMinutes()).isBetween(15, 20 * 60);
                }
            }
        }
    }
}
