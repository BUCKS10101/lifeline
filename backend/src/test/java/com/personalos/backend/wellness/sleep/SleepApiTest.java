package com.personalos.backend.wellness.sleep;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it. A night belongs to the date the person woke up. */
class SleepApiTest extends ApiTestBase {

    private static final String NIGHTS = "/api/v1/sleep-entries";
    private static final String SERIES = "/api/v1/sleep/series";
    private static final String TODAY = "2026-09-24";

    private MvcResult put(TestClient client, String date, String bed, String wake) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (bed != null) body.put("bedtime", bed);
        if (wake != null) body.put("wakeTime", wake);
        return client.put(NIGHTS + "/" + date, body);
    }

    private JsonNode log(TestClient client, String date, String bed, String wake) throws Exception {
        MvcResult result = put(client, date, bed, wake);
        assertThat(result.getResponse().getStatus()).isIn(200, 201);
        return client.json(result);
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from sleep_entries where user_id = ?", Integer.class, user);
    }

    // ---- logging -----------------------------------------------------------------------------

    @Test
    void aNightAcrossMidnightIsCreatedWith201AndAFullDescription() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);

        MvcResult created = put(client, TODAY, "23:30", "07:15");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo(NIGHTS + "/" + TODAY);
        JsonNode body = client.json(created);
        assertThat(body.get("date").asText()).isEqualTo(TODAY);
        assertThat(body.get("bedtimeDate").asText()).isEqualTo("2026-09-23");
        assertThat(body.get("bedtime").asText()).isEqualTo("23:30");
        assertThat(body.get("wakeTime").asText()).isEqualTo("07:15");
        assertThat(body.get("bedtimeAt").asText()).isEqualTo("2026-09-23T23:30:00Z");
        assertThat(body.get("wokeAt").asText()).isEqualTo("2026-09-24T07:15:00Z");
        assertThat(body.get("durationMinutes").asInt()).isEqualTo(465);
        assertThat(body.get("timeZone").asText()).isEqualTo("UTC");
        assertThat(rows(user)).isEqualTo(1);
    }

    @Test
    void sameMorningAndLongAfternoonSleepsAreBothReadCorrectly() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode sameMorning = log(client, "2026-09-22", "01:00", "08:00");
        assertThat(sameMorning.get("bedtimeDate").asText()).isEqualTo("2026-09-22");
        assertThat(sameMorning.get("durationMinutes").asInt()).isEqualTo(420);
        JsonNode afternoon = log(client, "2026-09-23", "14:00", "08:00");
        assertThat(afternoon.get("bedtimeDate").asText()).isEqualTo("2026-09-22");
        assertThat(afternoon.get("durationMinutes").asInt()).isEqualTo(18 * 60);
    }

    @Test
    void loggingTheSameMorningAgainReplacesItAndKeepsOneRowAndItsId() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        log(client, TODAY, "23:00", "07:00");
        UUID id = jdbc.queryForObject("select id from sleep_entries where user_id = ?", UUID.class, user);

        MvcResult replaced = put(client, TODAY, "00:30", "08:30");
        assertThat(replaced.getResponse().getStatus()).isEqualTo(200);
        assertThat(replaced.getResponse().getHeader("Location")).isNull();
        assertThat(client.json(replaced).get("durationMinutes").asInt()).isEqualTo(480);
        assertThat(rows(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id from sleep_entries where user_id = ?", UUID.class, user)).isEqualTo(id);
    }

    @Test
    void repeatingTheSameRequestIsSafe() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        assertThat(put(client, TODAY, "23:00", "07:00").getResponse().getStatus()).isEqualTo(201);
        MvcResult again = put(client, TODAY, "23:00", "07:00");
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(client.json(again).get("durationMinutes").asInt()).isEqualTo(480);
        assertThat(rows(user)).isEqualTo(1);
    }

    // ---- validation --------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"7:15", "24:00", "23:60", "23.30", "abc", "", "23:30:00", " 23:30"})
    void badClockTimesAreRejectedWithFieldErrors(String bad) throws Exception {
        TestClient client = newUser("a@example.com");
        MvcResult result = put(client, TODAY, bad, "07:00");
        expectError(client, result, 400, "VALIDATION_FAILED");
        assertThat(client.json(result).get("violations").toString()).contains("bedtime");
        expectError(client, put(client, TODAY, "23:00", bad), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(client))).isZero();
    }

    @Test
    void bothTimesAreRequired() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, put(client, TODAY, null, "07:00"), 400, "VALIDATION_FAILED");
        expectError(client, put(client, TODAY, "23:00", null), 400, "VALIDATION_FAILED");
        expectError(client, client.put(NIGHTS + "/" + TODAY, Map.of()), 400, "VALIDATION_FAILED");
    }

    @Test
    void implausibleDurationsAreRejectedWithClearMessagesAndNothingIsSaved() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        expectError(client, put(client, TODAY, "07:00", "07:00"), 400, "SLEEP_DURATION_INVALID");
        expectError(client, put(client, TODAY, "07:50", "08:00"), 400, "SLEEP_DURATION_INVALID");
        MvcResult tooLong = put(client, TODAY, "11:59", "08:00");
        expectError(client, tooLong, 400, "SLEEP_DURATION_INVALID");
        assertThat(client.json(tooLong).get("message").asText()).contains("longer than 20 hours");
        assertThat(rows(user)).isZero();
        assertThat(put(client, TODAY, "12:00", "08:00").getResponse().getStatus()).isEqualTo(201); // exactly 20 hours
    }

    @Test
    void aRejectedReplacementLeavesTheExistingNightUntouched() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, TODAY, "23:00", "07:00");
        expectError(client, put(client, TODAY, "11:59", "08:00"), 400, "SLEEP_DURATION_INVALID");
        assertThat(client.json(client.get(NIGHTS)).get("items").get(0).get("durationMinutes").asInt()).isEqualTo(480);
    }

    @Test
    void datesInTheFutureOrBefore2000AreRejected() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, put(client, "2026-09-25", "23:00", "07:00"), 400, "DATE_IN_FUTURE");
        expectError(client, put(client, "1999-12-31", "23:00", "07:00"), 400, "DATE_TOO_EARLY");
        expect(client, put(client, "2000-01-01", "23:00", "07:00"), 201);
        expect(client, put(client, TODAY, "23:00", "07:00"), 201);
        expect(client, put(client, "not-a-date", "23:00", "07:00"), 400);
        assertThat(rows(userId(client))).isEqualTo(2);
    }

    // ---- timezones and daylight saving -------------------------------------------------------

    @Test
    void todayAndTheStoredMomentsFollowThePersonsTimezone() throws Exception {
        // At 10:00 UTC it is already 2026-09-25 in Kiritimati (UTC+14), so that date is loggable there but not in UTC.
        TestClient kiritimati = newUser("k@example.com", "Pacific/Kiritimati");
        expect(kiritimati, put(kiritimati, "2026-09-25", "23:00", "07:00"), 201);
        TestClient utc = newUser("u@example.com");
        expectError(utc, put(utc, "2026-09-25", "23:00", "07:00"), 400, "DATE_IN_FUTURE");

        TestClient kolkata = newUser("i@example.com", "Asia/Kolkata");
        JsonNode body = log(kolkata, TODAY, "23:30", "07:15");
        assertThat(body.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(body.get("bedtimeAt").asText()).isEqualTo("2026-09-23T18:00:00Z");
        assertThat(body.get("wokeAt").asText()).isEqualTo("2026-09-24T01:45:00Z");
        assertThat(body.get("durationMinutes").asInt()).isEqualTo(465);
    }

    @Test
    void daylightSavingNightsHaveTheirRealElapsedTime() throws Exception {
        TestClient london = newUser("l@example.com", "Europe/London");
        clock.set(Instant.parse("2026-11-01T12:00:00Z"));
        assertThat(log(london, "2026-03-29", "23:00", "07:00").get("durationMinutes").asInt()).isEqualTo(7 * 60);   // clocks forward
        assertThat(log(london, "2026-10-25", "23:00", "07:00").get("durationMinutes").asInt()).isEqualTo(9 * 60);   // clocks back
        assertThat(log(london, "2026-10-20", "23:00", "07:00").get("durationMinutes").asInt()).isEqualTo(8 * 60);   // an ordinary night
        TestClient newYork = newUser("n@example.com", "America/New_York");
        assertThat(log(newYork, "2026-03-08", "23:00", "07:00").get("durationMinutes").asInt()).isEqualTo(7 * 60);
        assertThat(log(newYork, "2026-11-01", "23:00", "07:00").get("durationMinutes").asInt()).isEqualTo(9 * 60);
    }

    @Test
    void aLoggedNightKeepsItsOwnZoneWhenTheProfileTimezoneChangesLater() throws Exception {
        TestClient client = newUser("a@example.com", "Asia/Kolkata");
        log(client, TODAY, "23:30", "07:15");
        expect(client, client.patch("/api/v1/profile", Map.of("timezone", "America/New_York")), 200);

        JsonNode night = client.json(client.get(NIGHTS)).get("items").get(0);
        assertThat(night.get("bedtime").asText()).isEqualTo("23:30");   // still the times it was logged with
        assertThat(night.get("wakeTime").asText()).isEqualTo("07:15");
        assertThat(night.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(night.get("durationMinutes").asInt()).isEqualTo(465);
        assertThat(client.json(client.get(SERIES + "?from=" + TODAY + "&to=" + TODAY)).get("points").get(0).get("bedtime").asText()).isEqualTo("23:30");
    }

    // ---- delete and list ---------------------------------------------------------------------

    @Test
    void deleteRemovesTheNightAndAMissingOneIsNotFound() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, TODAY, "23:00", "07:00");
        expect(client, client.delete(NIGHTS + "/" + TODAY), 204);
        expectError(client, client.delete(NIGHTS + "/" + TODAY), 404, "NOT_FOUND");
        assertThat(rows(userId(client))).isZero();
    }

    @Test
    void listIsNewestFirstPagedAndInclusiveOnBothEnds() throws Exception {
        TestClient client = newUser("a@example.com");
        for (int day = 10; day <= 24; day++) log(client, "2026-09-" + day, "23:00", "07:00");

        JsonNode page = client.json(client.get(NIGHTS + "?size=5"));
        assertThat(page.get("totalItems").asInt()).isEqualTo(15);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("items").get(0).get("date").asText()).isEqualTo("2026-09-24");
        assertThat(page.get("items").get(4).get("date").asText()).isEqualTo("2026-09-20");
        assertThat(client.json(client.get(NIGHTS + "?size=5&page=2")).get("items").get(4).get("date").asText()).isEqualTo("2026-09-10");

        JsonNode ranged = client.json(client.get(NIGHTS + "?from=2026-09-12&to=2026-09-14"));
        assertThat(ranged.get("items")).hasSize(3);
        assertThat(ranged.get("items").get(0).get("date").asText()).isEqualTo("2026-09-14");
        assertThat(ranged.get("items").get(2).get("date").asText()).isEqualTo("2026-09-12");
        expectError(client, client.get(NIGHTS + "?from=2026-09-14&to=2026-09-12"), 400, "INVALID_RANGE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1", "size=x"})
    void listRejectsBadPagination(String query) throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.get(NIGHTS + "?" + query), 400);
    }

    // ---- series ------------------------------------------------------------------------------

    @Test
    void seriesListsNightsOldestFirstWithTheAverageAndThePreviousPeriodsAverage() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, "2026-09-18", "22:00", "07:00");   // 540, the previous period
        log(client, "2026-09-19", "21:00", "07:00");   // 600
        log(client, "2026-09-16", "20:00", "07:00");   // 660, one day before the previous period: must not be counted
        log(client, "2026-09-20", "23:00", "07:00");   // 480
        log(client, "2026-09-21", "00:00", "07:00");   // 420
        log(client, "2026-09-22", "01:00", "07:00");   // 360

        JsonNode series = client.json(client.get(SERIES + "?from=2026-09-20&to=2026-09-22"));
        assertThat(series.get("from").asText()).isEqualTo("2026-09-20");
        assertThat(series.get("to").asText()).isEqualTo("2026-09-22");
        JsonNode points = series.get("points");
        assertThat(points).hasSize(3);
        assertThat(points.get(0).get("date").asText()).isEqualTo("2026-09-20");
        assertThat(points.get(0).get("bedtime").asText()).isEqualTo("23:00");
        assertThat(points.get(0).get("wakeTime").asText()).isEqualTo("07:00");
        assertThat(points.get(0).get("durationMinutes").asInt()).isEqualTo(480);
        assertThat(points.get(2).get("durationMinutes").asInt()).isEqualTo(360);
        assertThat(series.get("average").get("durationMinutes").asInt()).isEqualTo(420);
        assertThat(series.get("average").get("entries").asInt()).isEqualTo(3);
        // The three days before 2026-09-20 are 09-17 to 09-19: two nights, (540 + 600) / 2.
        assertThat(series.get("previousAverage").get("durationMinutes").asInt()).isEqualTo(570);
        assertThat(series.get("previousAverage").get("entries").asInt()).isEqualTo(2);
    }

    @Test
    void seriesDefaultsToTheLastThirtyDaysEndingToday() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, "2026-08-26", "23:00", "07:00");   // 30 days ending 2026-09-24 start on 08-26
        log(client, "2026-08-25", "23:00", "07:00");   // one day earlier: not in range, but in the previous period
        JsonNode series = client.json(client.get(SERIES));
        assertThat(series.get("to").asText()).isEqualTo(TODAY);
        assertThat(series.get("from").asText()).isEqualTo("2026-08-26");
        assertThat(series.get("points")).hasSize(1);
        assertThat(series.get("previousAverage").get("entries").asInt()).isEqualTo(1);
    }

    @Test
    void seriesWithNoNightsIsEmptyWithNullAverages() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode series = client.json(client.get(SERIES));
        assertThat(series.get("points")).isEmpty();
        assertThat(series.get("average").isNull()).isTrue();
        assertThat(series.get("previousAverage").isNull()).isTrue();
    }

    @Test
    void aSingleNightIsItsOwnAverage() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, "2026-09-22", "23:00", "06:45");
        JsonNode average = client.json(client.get(SERIES)).get("average");
        assertThat(average.get("durationMinutes").asInt()).isEqualTo(465);
        assertThat(average.get("entries").asInt()).isEqualTo(1);
    }

    @Test
    void seriesAveragesRoundToWholeMinutes() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, "2026-09-21", "23:00", "07:00");   // 480
        log(client, "2026-09-22", "23:00", "07:01");   // 481
        assertThat(client.json(client.get(SERIES)).get("average").get("durationMinutes").asInt()).isEqualTo(481); // 480.5 rounds half up to a whole minute
    }

    @Test
    void seriesRejectsBackwardsAndOverlongRanges() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.get(SERIES + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
        expectError(client, client.get(SERIES + "?from=2025-01-01&to=2026-09-24"), 400, "INVALID_RANGE");
        expect(client, client.get(SERIES + "?from=2025-09-24&to=2026-09-24"), 200); // exactly 366 days
        expectError(client, client.get(SERIES + "?from=2025-09-23&to=2026-09-24"), 400, "INVALID_RANGE"); // 367 days: one too many
        expect(client, client.get(SERIES + "?from=nope"), 400);
    }

    // ---- ownership and access ----------------------------------------------------------------

    @Test
    void nightsAreInvisibleToAndUntouchableByOtherUsers() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        log(a, TODAY, "23:00", "07:00");

        assertThat(b.json(b.get(NIGHTS)).get("items")).isEmpty();
        assertThat(b.json(b.get(SERIES)).get("points")).isEmpty();
        expectError(b, b.delete(NIGHTS + "/" + TODAY), 404, "NOT_FOUND");
        assertThat(rows(userId(a))).isEqualTo(1);

        // b logging the same morning does not touch a's night.
        log(b, TODAY, "01:00", "08:00");
        assertThat(a.json(a.get(NIGHTS)).get("items").get(0).get("durationMinutes").asInt()).isEqualTo(480);
        assertThat(rows(userId(b))).isEqualTo(1);
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        for (String path : new String[]{NIGHTS, SERIES}) {
            assertThat(anon.get(path).getResponse().getStatus()).as(path).isEqualTo(401);
        }
        assertThat(anon.put(NIGHTS + "/" + TODAY, Map.of("bedtime", "23:00", "wakeTime", "07:00")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(NIGHTS + "/" + TODAY).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void mutationsWithoutACsrfTokenAreRejected() throws Exception {
        TestClient client = newUser("a@example.com");
        MvcResult put = client.call(HttpMethod.PUT, NIGHTS + "/" + TODAY, Map.of("bedtime", "23:00", "wakeTime", "07:00"), false);
        assertThat(put.getResponse().getStatus()).isEqualTo(403);
        assertThat(client.call(HttpMethod.DELETE, NIGHTS + "/" + TODAY, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(client))).isZero();
    }
}
