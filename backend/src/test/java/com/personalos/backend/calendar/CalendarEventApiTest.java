package com.personalos.backend.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it, so today is 2026-09-24 in UTC. */
class CalendarEventApiTest extends ApiTestBase {

    private static final String EVENTS = "/api/v1/calendar/events";

    private MvcResult post(TestClient c, Map<String, Object> body) throws Exception {
        return c.post(EVENTS, body);
    }

    private static Map<String, Object> body(String title, String startDate, String startTime) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        if (startDate != null) m.put("startDate", startDate);
        if (startTime != null) m.put("startTime", startTime);
        return m;
    }

    private List<String> titlesOn(TestClient c, String date) throws Exception {
        return titlesBetween(c, date, date);
    }

    private List<String> titlesBetween(TestClient c, String from, String to) throws Exception {
        List<String> out = new ArrayList<>();
        expect(c, c.get(EVENTS + "?from=" + from + "&to=" + to), 200).forEach(e -> out.add(e.get("title").asText()));
        return out;
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from calendar_events where user_id = ?", Integer.class, user);
    }

    // ---- creating ------------------------------------------------------------------------------

    @Test
    void aTimedEventWithOnlyRequiredFieldsGetsSensibleDefaults() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode e = expect(c, post(c, body("  Dentist  ", "2026-10-01", "09:00")), 201);
        assertThat(e.get("id").asText()).isNotBlank();
        assertThat(e.get("title").asText()).isEqualTo("Dentist");
        assertThat(e.get("description").isNull()).isTrue();
        assertThat(e.get("startDate").asText()).isEqualTo("2026-10-01");
        assertThat(e.get("startTime").asText()).isEqualTo("09:00");
        assertThat(e.get("endDate").isNull()).isTrue();
        assertThat(e.get("endTime").isNull()).isTrue();
        assertThat(e.get("allDay").asBoolean()).isFalse();
        assertThat(e.get("timeZone").asText()).isEqualTo("UTC");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void anEventCanCarryADescriptionAndAnEnd() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Flight", "2026-10-01", "23:00");
        b.put("description", "  Gate closes 30 min before  ");
        b.put("endDate", "2026-10-02");
        b.put("endTime", "02:00");
        JsonNode e = expect(c, post(c, b), 201);
        assertThat(e.get("description").asText()).isEqualTo("Gate closes 30 min before");
        assertThat(e.get("endDate").asText()).isEqualTo("2026-10-02");
        assertThat(e.get("endTime").asText()).isEqualTo("02:00");
    }

    @Test
    void anAllDayEventIgnoresTimesAndCanSpanMultipleDays() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Vacation", "2026-10-10", null);
        b.put("endDate", "2026-10-12");
        b.put("allDay", true);
        JsonNode e = expect(c, post(c, b), 201);
        assertThat(e.get("allDay").asBoolean()).isTrue();
        assertThat(e.get("startTime").isNull()).isTrue();
        assertThat(e.get("endTime").isNull()).isTrue();
        assertThat(e.get("endDate").asText()).isEqualTo("2026-10-12");
        // Inclusive: it appears on the start day, the middle day and the end day, and not the day after.
        assertThat(titlesOn(c, "2026-10-10")).containsExactly("Vacation");
        assertThat(titlesOn(c, "2026-10-11")).containsExactly("Vacation");
        assertThat(titlesOn(c, "2026-10-12")).containsExactly("Vacation");
        assertThat(titlesOn(c, "2026-10-13")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankTitleIsRejected(String title) throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body(title, "2026-10-01", "09:00")), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void lengthsAndTimesAreValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("x".repeat(201), "2026-10-01", "09:00")), 400, "VALIDATION_FAILED");
        expect(c, post(c, body("x".repeat(200), "2026-10-01", "09:00")), 201);
        Map<String, Object> longDesc = body("t", "2026-10-01", "09:00");
        longDesc.put("description", "n".repeat(2001));
        expectError(c, post(c, longDesc), 400, "VALIDATION_FAILED");
        expectError(c, post(c, body("t", "2026-10-01", "25:00")), 400, "VALIDATION_FAILED");
        expectError(c, post(c, body("t", "2026-10-01", "not-a-time")), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aTimedEventRequiresAStartTime() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("t", "2026-10-01", null)), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void endDateAndEndTimeMustBeGivenTogether() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> onlyDate = body("t", "2026-10-01", "09:00");
        onlyDate.put("endDate", "2026-10-02");
        expectError(c, post(c, onlyDate), 400, "VALIDATION_FAILED");
        Map<String, Object> onlyTime = body("t", "2026-10-01", "09:00");
        onlyTime.put("endTime", "10:00");
        expectError(c, post(c, onlyTime), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void anEndBeforeTheStartIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("t", "2026-10-01", "10:00");
        b.put("endDate", "2026-10-01");
        b.put("endTime", "09:00");
        expectError(c, post(c, b), 400, "INVALID_RANGE");
        Map<String, Object> allDay = body("t", "2026-10-05", null);
        allDay.put("allDay", true);
        allDay.put("endDate", "2026-10-01");
        expectError(c, post(c, allDay), 400, "INVALID_RANGE");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void datesAreBoundedTo2000Through2100() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("t", "1999-12-31", "09:00")), 400, "DATE_TOO_EARLY");
        expectError(c, post(c, body("t", "2101-01-01", "09:00")), 400, "DATE_TOO_LATE");
        assertThat(rows(userId(c))).isZero();
    }

    // ---- a timed event spanning midnight appears on both days ------------------------------------

    @Test
    void aTimedEventSpanningMidnightAppearsOnBothDays() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Red-eye", "2026-10-01", "23:00");
        b.put("endDate", "2026-10-02");
        b.put("endTime", "02:00");
        expect(c, post(c, b), 201);
        assertThat(titlesOn(c, "2026-10-01")).containsExactly("Red-eye");
        assertThat(titlesOn(c, "2026-10-02")).containsExactly("Red-eye");
        assertThat(titlesOn(c, "2026-09-30")).isEmpty();
    }

    // ---- listing ---------------------------------------------------------------------------------

    @Test
    void listingByRangeExcludesEventsOutsideTheWindow() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, post(c, body("Before", "2026-09-01", "09:00")), 201);
        expect(c, post(c, body("In range", "2026-10-15", "09:00")), 201);
        expect(c, post(c, body("After", "2026-11-01", "09:00")), 201);
        assertThat(titlesBetween(c, "2026-10-01", "2026-10-31")).containsExactly("In range");
    }

    @Test
    void anUnknownOrBackwardsRangeIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.get(EVENTS + "?from=2026-10-05&to=2026-10-01"), 400);
        expect(c, c.get(EVENTS + "?from=not-a-date&to=2026-10-01"), 400);
        expect(c, c.get(EVENTS + "?from=2026-01-01&to=2027-06-01"), 400); // more than 370 days
    }

    // ---- editing -----------------------------------------------------------------------------

    private JsonNode patch(TestClient c, String id, Map<String, Object> body) throws Exception {
        return expect(c, c.patch(EVENTS + "/" + id, body), 200);
    }

    @Test
    void aPartialEditChangesOnlyWhatIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Original", "2026-10-01", "09:00");
        b.put("description", "keep me");
        String id = expect(c, post(c, b), 201).get("id").asText();

        JsonNode e = patch(c, id, Map.of("title", "  Renamed  "));
        assertThat(e.get("title").asText()).isEqualTo("Renamed");
        assertThat(e.get("description").asText()).isEqualTo("keep me");
        assertThat(e.get("startTime").asText()).isEqualTo("09:00");

        e = patch(c, id, Map.of("startTime", "10:30"));
        assertThat(e.get("startTime").asText()).isEqualTo("10:30");
        assertThat(e.get("title").asText()).isEqualTo("Renamed");
    }

    @Test
    void anExplicitNullClearsDescriptionAndTheEndButAnOmittedFieldDoesNot() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("T", "2026-10-01", "09:00");
        b.put("description", "d");
        b.put("endDate", "2026-10-01");
        b.put("endTime", "10:00");
        String id = expect(c, post(c, b), 201).get("id").asText();

        Map<String, Object> clearDescription = new HashMap<>();
        clearDescription.put("description", null);
        JsonNode e = patch(c, id, clearDescription);
        assertThat(e.get("description").isNull()).isTrue();
        assertThat(e.get("endDate").asText()).isEqualTo("2026-10-01"); // untouched

        Map<String, Object> clearEnd = new HashMap<>();
        clearEnd.put("endDate", null);
        clearEnd.put("endTime", null);
        e = patch(c, id, clearEnd);
        assertThat(e.get("endDate").isNull()).isTrue();
        assertThat(e.get("endTime").isNull()).isTrue();
    }

    @Test
    void clearingOnlyOneOfEndDateOrEndTimeIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("T", "2026-10-01", "09:00");
        b.put("endDate", "2026-10-01");
        b.put("endTime", "10:00");
        String id = expect(c, post(c, b), 201).get("id").asText();
        Map<String, Object> clearDateOnly = new HashMap<>();
        clearDateOnly.put("endDate", null);
        expectError(c, c.patch(EVENTS + "/" + id, clearDateOnly), 400, "VALIDATION_FAILED");
    }

    @Test
    void togglingAllDayOnAndOffWorks() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        JsonNode e = patch(c, id, Map.of("allDay", true));
        assertThat(e.get("allDay").asBoolean()).isTrue();
        assertThat(e.get("startTime").isNull()).isTrue();
        e = patch(c, id, Map.of("allDay", false, "startTime", "08:00"));
        assertThat(e.get("allDay").asBoolean()).isFalse();
        assertThat(e.get("startTime").asText()).isEqualTo("08:00");
    }

    @Test
    void togglingAllDayOffWithoutAStartTimeIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("T", "2026-10-01", null);
        b.put("allDay", true);
        String id = expect(c, post(c, b), 201).get("id").asText();
        expectError(c, c.patch(EVENTS + "/" + id, Map.of("allDay", false)), 400, "VALIDATION_FAILED");
    }

    @Test
    void editValidationRejectsBadValuesAndAnEmptyBodyAndChangesNothing() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("Keep", "2026-10-01", "09:00")), 201).get("id").asText();
        expectError(c, c.patch(EVENTS + "/" + id, Map.of()), 400, "EMPTY_UPDATE");
        Map<String, Object> nullTitle = new HashMap<>();
        nullTitle.put("title", null);
        expectError(c, c.patch(EVENTS + "/" + id, nullTitle), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(EVENTS + "/" + id, Map.of("title", "   ")), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(EVENTS + "/" + id, Map.of("startDate", "1999-12-31")), 400, "DATE_TOO_EARLY");

        JsonNode unchanged = expect(c, c.get(EVENTS + "?from=2026-10-01&to=2026-10-01"), 200).get(0);
        assertThat(unchanged.get("title").asText()).isEqualTo("Keep");
    }

    // ---- timezone --------------------------------------------------------------------------------

    @Test
    void anEventKeepsItsOwnZoneWhenTheProfileTimezoneChangesLater() throws Exception {
        TestClient c = newUser("a@example.com", "Asia/Kolkata");
        expect(c, post(c, body("T", "2026-10-01", "23:30")), 201);
        expect(c, c.patch("/api/v1/profile", Map.of("timezone", "America/New_York")), 200);

        JsonNode e = expect(c, c.get(EVENTS + "?from=2026-10-01&to=2026-10-01"), 200).get(0);
        assertThat(e.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(e.get("startTime").asText()).isEqualTo("23:30"); // still the time it was logged with
    }

    @Test
    void theDayABoundaryEventAppearsOnIsThePersonsOwnTimezoneNotUtc() throws Exception {
        // At 10:00 UTC on 2026-09-24 it is already 2026-09-25 in Kiritimati (UTC+14).
        TestClient far = newUser("k@example.com", "Pacific/Kiritimati");
        expect(far, post(far, body("Early", "2026-09-25", "00:30")), 201);
        assertThat(titlesOn(far, "2026-09-25")).containsExactly("Early");
        assertThat(titlesOn(far, "2026-09-24")).isEmpty();
    }

    // ---- delete and access -----------------------------------------------------------------------

    @Test
    void deleteRemovesTheEventAndAMissingOneIsNotFound() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        expect(c, c.delete(EVENTS + "/" + id), 204);
        expectError(c, c.delete(EVENTS + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void anotherUsersEventCannotBeSeenEditedOrDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, post(a, body("Mine", "2026-10-01", "09:00")), 201).get("id").asText();
        expectError(b, b.patch(EVENTS + "/" + id, Map.of("title", "Hijack")), 404, "NOT_FOUND");
        expectError(b, b.delete(EVENTS + "/" + id), 404, "NOT_FOUND");
        assertThat(titlesOn(b, "2026-10-01")).isEmpty();
        assertThat(titlesOn(a, "2026-10-01")).containsExactly("Mine");
    }

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(EVENTS + "?from=2026-10-01&to=2026-10-01").getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(EVENTS, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(EVENTS + "/" + id, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(EVENTS + "/" + id).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        assertThat(c.call(HttpMethod.POST, EVENTS, Map.of("title", "t"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, EVENTS + "/" + mine, Map.of("title", "x"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, EVENTS + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
