package com.personalos.backend.reminders;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it. */
class ReminderApiTest extends ApiTestBase {

    private static final String REMINDERS = "/api/v1/reminders";

    private MvcResult post(TestClient c, Map<String, Object> body) throws Exception {
        return c.post(REMINDERS, body);
    }

    private static Map<String, Object> body(String title, String date, String time) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        if (date != null) m.put("date", date);
        if (time != null) m.put("time", time);
        return m;
    }

    private List<String> titles(TestClient c, String query) throws Exception {
        List<String> out = new ArrayList<>();
        expect(c, c.get(REMINDERS + query), 200).forEach(r -> out.add(r.get("title").asText()));
        return out;
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from reminders where user_id = ?", Integer.class, user);
    }

    // ---- creating ------------------------------------------------------------------------------

    @Test
    void aReminderCarriesItsTitleMomentAndZone() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode r = expect(c, post(c, body("  Pay rent  ", "2026-10-01", "09:00")), 201);
        assertThat(r.get("id").asText()).isNotBlank();
        assertThat(r.get("title").asText()).isEqualTo("Pay rent");
        assertThat(r.get("date").asText()).isEqualTo("2026-10-01");
        assertThat(r.get("time").asText()).isEqualTo("09:00");
        assertThat(r.get("timeZone").asText()).isEqualTo("UTC");
        assertThat(r.get("completedAt").isNull()).isTrue();
        assertThat(r.get("due").asBoolean()).isFalse(); // in the future
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankTitleIsRejected(String title) throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body(title, "2026-10-01", "09:00")), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void lengthAndFieldsAreValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("x".repeat(201), "2026-10-01", "09:00")), 400, "VALIDATION_FAILED");
        expect(c, post(c, body("x".repeat(200), "2026-10-01", "09:00")), 201);
        expect(c, post(c, body("t", "2026-10-01", "25:00")), 400);
        expect(c, post(c, body("t", "not-a-date", "09:00")), 400);
        expect(c, post(c, body("t", "2026-10-01", null)), 400);
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void datesAreBoundedTo2000Through2100() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("t", "1999-12-31", "09:00")), 400, "DATE_TOO_EARLY");
        expectError(c, post(c, body("t", "2101-01-01", "09:00")), 400, "DATE_TOO_LATE");
        assertThat(rows(userId(c))).isZero();
    }

    // ---- due/upcoming ------------------------------------------------------------------------

    @Test
    void dueReflectsWhetherTheMomentHasArrivedAndIsNotCompleted() throws Exception {
        TestClient c = newUser("a@example.com"); // clock: 2026-09-24T10:00:00Z
        String past = expect(c, post(c, body("Past", "2026-09-24", "09:00")), 201).get("id").asText();
        String future = expect(c, post(c, body("Future", "2026-09-24", "11:00")), 201).get("id").asText();
        assertThat(expect(c, c.get(REMINDERS), 200)).hasSize(2);
        JsonNode pastR = find(c, past);
        JsonNode futureR = find(c, future);
        assertThat(pastR.get("due").asBoolean()).isTrue();
        assertThat(futureR.get("due").asBoolean()).isFalse();
        clock.advance(Duration.ofHours(2));
        assertThat(find(c, future).get("due").asBoolean()).isTrue(); // the clock caught up to it
    }

    @Test
    void aCompletedReminderIsNeverDue() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("Past", "2026-09-24", "09:00")), 201).get("id").asText();
        assertThat(find(c, id).get("due").asBoolean()).isTrue();
        expect(c, c.post(REMINDERS + "/" + id + "/complete", Map.of()), 200);
        assertThat(find(c, id).get("due").asBoolean()).isFalse();
    }

    private JsonNode find(TestClient c, String id) throws Exception {
        for (JsonNode r : expect(c, c.get(REMINDERS + "?includeCompleted=true"), 200)) {
            if (r.get("id").asText().equals(id)) return r;
        }
        throw new AssertionError("not found: " + id);
    }

    // ---- listing: pending first, then completed when asked for ----------------------------------

    @Test
    void listDefaultsToPendingSoonestFirstAndHidesCompleted() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, post(c, body("Later", "2026-10-05", "09:00")), 201);
        expect(c, post(c, body("Sooner", "2026-10-01", "09:00")), 201);
        String done = expect(c, post(c, body("Done", "2026-09-20", "09:00")), 201).get("id").asText();
        expect(c, c.post(REMINDERS + "/" + done + "/complete", Map.of()), 200);

        assertThat(titles(c, "")).containsExactly("Sooner", "Later"); // completed hidden, soonest first
        assertThat(titles(c, "?includeCompleted=true")).containsExactly("Sooner", "Later", "Done");
    }

    // ---- complete and reopen -----------------------------------------------------------------

    @Test
    void completingAndReopeningAreIdempotent() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        JsonNode done = expect(c, c.post(REMINDERS + "/" + id + "/complete", Map.of()), 200);
        assertThat(done.get("completedAt").isNull()).isFalse();
        String at = done.get("completedAt").asText();
        clock.advance(Duration.ofHours(3));
        JsonNode again = expect(c, c.post(REMINDERS + "/" + id + "/complete", Map.of()), 200);
        assertThat(again.get("completedAt").asText()).isEqualTo(at); // the original moment is kept

        JsonNode reopened = expect(c, c.post(REMINDERS + "/" + id + "/reopen", Map.of()), 200);
        assertThat(reopened.get("completedAt").isNull()).isTrue();
        JsonNode reopenedAgain = expect(c, c.post(REMINDERS + "/" + id + "/reopen", Map.of()), 200);
        assertThat(reopenedAgain.get("completedAt").isNull()).isTrue();
    }

    @Test
    void completingOrReopeningSomeoneElsesReminderIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, post(a, body("Mine", "2026-10-01", "09:00")), 201).get("id").asText();
        expectError(b, b.post(REMINDERS + "/" + id + "/complete", Map.of()), 404, "NOT_FOUND");
        expectError(b, b.post(REMINDERS + "/" + id + "/reopen", Map.of()), 404, "NOT_FOUND");
    }

    // ---- editing -----------------------------------------------------------------------------

    private JsonNode patch(TestClient c, String id, Map<String, Object> body) throws Exception {
        return expect(c, c.patch(REMINDERS + "/" + id, body), 200);
    }

    @Test
    void aPartialEditChangesOnlyWhatIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("Original", "2026-10-01", "09:00")), 201).get("id").asText();
        JsonNode r = patch(c, id, Map.of("title", "  Renamed  "));
        assertThat(r.get("title").asText()).isEqualTo("Renamed");
        assertThat(r.get("date").asText()).isEqualTo("2026-10-01");
        assertThat(r.get("time").asText()).isEqualTo("09:00");

        r = patch(c, id, Map.of("date", "2026-11-01"));
        assertThat(r.get("date").asText()).isEqualTo("2026-11-01");
        assertThat(r.get("time").asText()).isEqualTo("09:00"); // untouched
        assertThat(r.get("title").asText()).isEqualTo("Renamed");
    }

    @Test
    void nothingCanBeClearedAndAnEmptyBodyIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        expectError(c, c.patch(REMINDERS + "/" + id, Map.of()), 400, "EMPTY_UPDATE");
        Map<String, Object> nullTitle = new HashMap<>();
        nullTitle.put("title", null);
        expectError(c, c.patch(REMINDERS + "/" + id, nullTitle), 400, "VALIDATION_FAILED");
        Map<String, Object> nullDate = new HashMap<>();
        nullDate.put("date", null);
        expectError(c, c.patch(REMINDERS + "/" + id, nullDate), 400, "VALIDATION_FAILED");
        Map<String, Object> nullTime = new HashMap<>();
        nullTime.put("time", null);
        expectError(c, c.patch(REMINDERS + "/" + id, nullTime), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(REMINDERS + "/" + id, Map.of("date", "1999-12-31")), 400, "DATE_TOO_EARLY");
    }

    // ---- timezone --------------------------------------------------------------------------------

    @Test
    void aReminderKeepsItsOwnZoneWhenTheProfileTimezoneChangesLater() throws Exception {
        TestClient c = newUser("a@example.com", "Asia/Kolkata");
        expect(c, post(c, body("T", "2026-10-01", "23:30")), 201);
        expect(c, c.patch("/api/v1/profile", Map.of("timezone", "America/New_York")), 200);
        JsonNode r = expect(c, c.get(REMINDERS), 200).get(0);
        assertThat(r.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(r.get("time").asText()).isEqualTo("23:30");
    }

    // ---- delete and access -----------------------------------------------------------------------

    @Test
    void deleteRemovesTheReminderAndAMissingOneIsNotFound() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        expect(c, c.delete(REMINDERS + "/" + id), 204);
        expectError(c, c.delete(REMINDERS + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void anotherUsersReminderCannotBeSeenEditedOrDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, post(a, body("Mine", "2026-10-01", "09:00")), 201).get("id").asText();
        expectError(b, b.patch(REMINDERS + "/" + id, Map.of("title", "Hijack")), 404, "NOT_FOUND");
        expectError(b, b.delete(REMINDERS + "/" + id), 404, "NOT_FOUND");
        assertThat(titles(b, "")).isEmpty();
        assertThat(titles(a, "")).containsExactly("Mine");
    }

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(REMINDERS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(REMINDERS, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(REMINDERS + "/" + id, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(REMINDERS + "/" + id + "/complete", Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(REMINDERS + "/" + id).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = expect(c, post(c, body("T", "2026-10-01", "09:00")), 201).get("id").asText();
        assertThat(c.call(HttpMethod.POST, REMINDERS, Map.of("title", "t"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, REMINDERS + "/" + mine, Map.of("title", "x"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.POST, REMINDERS + "/" + mine + "/complete", Map.of(), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, REMINDERS + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
