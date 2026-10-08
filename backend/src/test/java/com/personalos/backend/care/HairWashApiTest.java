package com.personalos.backend.care;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it, so today is 2026-09-24 in UTC. */
class HairWashApiTest extends ApiTestBase {

    private static final String HAIR_WASH = "/api/v1/personal-care/hair-wash";

    private MvcResult mark(TestClient c, String date) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (date != null) body.put("date", date);
        return c.post(HAIR_WASH, body);
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from hair_wash_entries where user_id = ?", Integer.class, user);
    }

    // ---- marking -------------------------------------------------------------------------------

    @Test
    void markingWithNoDateUsesToday() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode e = expect(c, mark(c, null), 201);
        assertThat(e.get("id").asText()).isNotBlank();
        assertThat(e.get("washDate").asText()).isEqualTo("2026-09-24");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void anExplicitPastDateCanBeMarked() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode e = expect(c, mark(c, "2026-09-20"), 201);
        assertThat(e.get("washDate").asText()).isEqualTo("2026-09-20");
    }

    @Test
    void aFutureDateIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, mark(c, "2026-09-25"), 400, "DATE_IN_FUTURE");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void datesBefore2000AreRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, mark(c, "1999-12-31"), 400, "DATE_TOO_EARLY");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void markingTheSameDateTwiceIsIdempotentNotDuplicated() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode first = expect(c, mark(c, "2026-09-20"), 201);
        JsonNode again = expect(c, mark(c, "2026-09-20"), 200); // 200, not 201: it already existed
        assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    // ---- summary: last washed, days ago, and the requested month's entries ----------------------

    @Test
    void withNoEntriesTheSummaryHasNullLastWashedAndNullDaysAgo() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode s = expect(c, c.get(HAIR_WASH), 200);
        assertThat(s.get("lastWashedOn").isNull()).isTrue();
        assertThat(s.get("daysAgo").isNull()).isTrue();
        assertThat(s.get("entries")).isEmpty();
    }

    @Test
    void lastWashedAndDaysAgoReflectTheMostRecentEntryRegardlessOfTheRequestedMonth() throws Exception {
        TestClient c = newUser("a@example.com");
        mark(c, "2026-08-01");
        mark(c, "2026-09-21"); // 3 days before today (2026-09-24)
        JsonNode s = expect(c, c.get(HAIR_WASH), 200);
        assertThat(s.get("lastWashedOn").asText()).isEqualTo("2026-09-21");
        assertThat(s.get("daysAgo").asInt()).isEqualTo(3);
        assertThat(s.get("month").asText()).isEqualTo("2026-09"); // defaults to the person's current month
    }

    @Test
    void daysAgoIsZeroWhenTheMostRecentWashWasToday() throws Exception {
        TestClient c = newUser("a@example.com");
        mark(c, null); // today
        assertThat(expect(c, c.get(HAIR_WASH), 200).get("daysAgo").asInt()).isZero();
    }

    @Test
    void theMonthParameterFiltersEntriesButNotLastWashed() throws Exception {
        TestClient c = newUser("a@example.com");
        mark(c, "2026-08-05");
        mark(c, "2026-09-10");
        JsonNode august = expect(c, c.get(HAIR_WASH + "?month=2026-08"), 200);
        assertThat(august.get("entries")).hasSize(1);
        assertThat(august.get("entries").get(0).get("washDate").asText()).isEqualTo("2026-08-05");
        assertThat(august.get("lastWashedOn").asText()).isEqualTo("2026-09-10"); // the whole history's latest, not August's
        JsonNode empty = expect(c, c.get(HAIR_WASH + "?month=2026-07"), 200);
        assertThat(empty.get("entries")).isEmpty();
        expect(c, c.get(HAIR_WASH + "?month=not-a-month"), 400);
    }

    // ---- editing (moving an entry to a different date) -------------------------------------------

    @Test
    void editingMovesTheEntryToADifferentDate() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, mark(c, "2026-09-10"), 201).get("id").asText();
        JsonNode moved = expect(c, c.patch(HAIR_WASH + "/" + id, Map.of("date", "2026-09-11")), 200);
        assertThat(moved.get("washDate").asText()).isEqualTo("2026-09-11");
        assertThat(expect(c, c.get(HAIR_WASH + "?month=2026-09"), 200).get("entries")).hasSize(1);
    }

    @Test
    void movingAnEntryOntoAnotherEntrysDateIsAConflict() throws Exception {
        TestClient c = newUser("a@example.com");
        mark(c, "2026-09-10");
        String id = expect(c, mark(c, "2026-09-11"), 201).get("id").asText();
        expectError(c, c.patch(HAIR_WASH + "/" + id, Map.of("date", "2026-09-10")), 409, "DUPLICATE_ENTRY");
        assertThat(rows(userId(c))).isEqualTo(2);
    }

    @Test
    void editingToAFutureDateOrMissingDateIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, mark(c, "2026-09-10"), 201).get("id").asText();
        expectError(c, c.patch(HAIR_WASH + "/" + id, Map.of("date", "2026-09-25")), 400, "DATE_IN_FUTURE");
        expect(c, c.patch(HAIR_WASH + "/" + id, Map.of()), 400);
    }

    @Test
    void editingSomeoneElsesEntryIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, mark(a, "2026-09-10"), 201).get("id").asText();
        expectError(b, b.patch(HAIR_WASH + "/" + id, Map.of("date", "2026-09-11")), 404, "NOT_FOUND");
    }

    // ---- delete and access -----------------------------------------------------------------------

    @Test
    void deleteRemovesTheEntryAndAMissingOneIsNotFound() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, mark(c, "2026-09-10"), 201).get("id").asText();
        expect(c, c.delete(HAIR_WASH + "/" + id), 204);
        expectError(c, c.delete(HAIR_WASH + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void anotherUsersEntryCannotBeSeenEditedOrDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, mark(a, "2026-09-10"), 201).get("id").asText();
        expectError(b, b.delete(HAIR_WASH + "/" + id), 404, "NOT_FOUND");
        assertThat(expect(b, b.get(HAIR_WASH + "?month=2026-09"), 200).get("entries")).isEmpty();
        assertThat(expect(b, b.get(HAIR_WASH), 200).get("lastWashedOn").isNull()).isTrue();
    }

    @Test
    void theBoundaryIsTheDateInThePersonsTimezoneNotUtc() throws Exception {
        // At 10:00 UTC on 2026-09-24 it is already 2026-09-25 in Kiritimati (UTC+14).
        TestClient far = newUser("k@example.com", "Pacific/Kiritimati");
        JsonNode e = expect(far, mark(far, null), 201);
        assertThat(e.get("washDate").asText()).isEqualTo("2026-09-25");
        expectError(far, mark(far, "2026-09-26"), 400, "DATE_IN_FUTURE"); // tomorrow in their own zone
    }

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(HAIR_WASH).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(HAIR_WASH, Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(HAIR_WASH + "/" + id, Map.of("date", "2026-09-10")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(HAIR_WASH + "/" + id).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = expect(c, mark(c, "2026-09-10"), 201).get("id").asText();
        assertThat(c.call(HttpMethod.POST, HAIR_WASH, Map.of(), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, HAIR_WASH + "/" + mine, Map.of("date", "2026-09-11"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, HAIR_WASH + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
