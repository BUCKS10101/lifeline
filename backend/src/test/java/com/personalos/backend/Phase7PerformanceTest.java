package com.personalos.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A smoke test on a realistic personal scale across Phase 7's three modules together: about two years of calendar
 * events, reminders and hair-wash entries. It checks that a month's worth of each stays correct and fast on a
 * realistic personal history, not that it is the fastest possible. The limit is deliberately generous so it does not
 * flake on a slow CI machine.
 */
class Phase7PerformanceTest extends ApiTestBase {

    private static final long LIMIT_MS = 3000;
    private static final int DAYS = 730; // two years

    private JsonNode timed(TestClient c, String label, String path) throws Exception {
        long start = System.nanoTime();
        JsonNode body = expect(c, c.get(path), 200);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("phase7 perf: %-32s %4d ms%n", label, ms);
        assertThat(ms).as(label + " took " + ms + " ms").isLessThan(LIMIT_MS);
        return body;
    }

    @Test
    void everyPhase7ReadIsCorrectAndFastOnTwoYearsOfData() throws Exception {
        TestClient c = newUser("perf@example.com");
        UUID user = userId(c);

        // Calendar events: one every day for two years, alternating timed and all-day.
        jdbc.update("""
                insert into calendar_events (id, user_id, title, start_at, end_at, zone_id, all_day, created_at, updated_at)
                select gen_random_uuid(), ?, 'Event ' || n,
                       (date '2026-09-24' - (%d - n))::timestamp at time zone 'UTC' + interval '9 hours',
                       null, 'UTC', (n %% 2 = 0), now(), now()
                from generate_series(1, %d) as n
                """.formatted(DAYS, DAYS), user);
        // Reminders: one every other day for two years.
        jdbc.update("""
                insert into reminders (id, user_id, title, remind_at, zone_id, created_at, updated_at)
                select gen_random_uuid(), ?, 'Reminder ' || n,
                       (date '2026-09-24' - (%d - n * 2))::timestamp at time zone 'UTC' + interval '8 hours',
                       'UTC', now(), now()
                from generate_series(1, %d) as n
                """.formatted(DAYS, DAYS / 2), user);
        // Hair wash: twice a week for two years (~200 entries).
        jdbc.update("""
                insert into hair_wash_entries (id, user_id, wash_date, created_at, updated_at)
                select gen_random_uuid(), ?, date '2026-09-24' - d, now(), now()
                from generate_series(0, %d - 1) as d where d %% 3 = 0
                """.formatted(DAYS), user);

        long eventCount = jdbc.queryForObject("select count(*) from calendar_events where user_id = ?", Long.class, user);
        long reminderCount = jdbc.queryForObject("select count(*) from reminders where user_id = ?", Long.class, user);
        long washCount = jdbc.queryForObject("select count(*) from hair_wash_entries where user_id = ?", Long.class, user);
        assertThat(eventCount).isEqualTo(DAYS);
        assertThat(reminderCount).isEqualTo(DAYS / 2);
        assertThat(washCount).isGreaterThan(200);

        // The seed runs up to and including today (2026-09-24), so September 2026 has events for its first 24 days only.
        JsonNode monthEvents = timed(c, "calendar, one month of " + DAYS + " days", "/api/v1/calendar/events?from=2026-09-01&to=2026-09-30");
        assertThat(monthEvents).hasSize(24);

        JsonNode reminders = timed(c, "reminders, full list (" + (DAYS / 2) + ")", "/api/v1/reminders?includeCompleted=true");
        assertThat(reminders).hasSize((int) reminderCount);

        JsonNode washSummary = timed(c, "hair wash, summary + one month", "/api/v1/personal-care/hair-wash?month=2026-09");
        assertThat(washSummary.get("lastWashedOn").isNull()).isFalse();
        assertThat(washSummary.get("entries")).isNotEmpty();
    }
}
