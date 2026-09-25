package com.personalos.backend.weight;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it. */
class WeightEntryApiTest extends ApiTestBase {

    private static final String ENTRIES = "/api/v1/weight-entries";
    private static final String TODAY = "2026-09-24";

    private MvcResult put(TestClient client, String date, Object weight, String notes) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (weight != null) body.put("weightKg", weight);
        if (notes != null) body.put("notes", notes);
        return client.put(ENTRIES + "/" + date, body);
    }

    private JsonNode log(TestClient client, String date, String weight) throws Exception {
        MvcResult result = put(client, date, new BigDecimal(weight), null);
        assertThat(result.getResponse().getStatus()).isIn(200, 201);
        return client.json(result);
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, user);
    }

    private void insertEntry(UUID user, String date, String weight) {
        jdbc.update("insert into weight_entries (id, user_id, entry_date, weight_kg) values (?, ?, ?::date, ?::numeric)",
                UUID.randomUUID(), user, date, weight);
    }

    // ---- create / replace --------------------------------------------------------------------

    @Test
    void createsAnEntryWith201AndALocationThenReplacesItWith200() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);

        MvcResult created = put(client, TODAY, new BigDecimal("82.4"), "morning, fasted");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo(ENTRIES + "/" + TODAY);
        JsonNode body = client.json(created);
        assertThat(body.get("date").asText()).isEqualTo(TODAY);
        assertThat(body.get("weightKg").decimalValue()).isEqualByComparingTo("82.40");
        assertThat(body.get("notes").asText()).isEqualTo("morning, fasted");
        UUID id = jdbc.queryForObject("select id from weight_entries where user_id = ?", UUID.class, user);

        clock.advance(Duration.ofHours(2));
        MvcResult replaced = put(client, TODAY, new BigDecimal("82.9"), "after lunch");
        assertThat(replaced.getResponse().getStatus()).isEqualTo(200);
        assertThat(replaced.getResponse().getHeader("Location")).isNull();
        assertThat(client.json(replaced).get("weightKg").decimalValue()).isEqualByComparingTo("82.90");

        assertThat(rows(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id from weight_entries where user_id = ?", UUID.class, user)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select weight_kg from weight_entries where user_id = ?", BigDecimal.class, user)).isEqualByComparingTo("82.90");
        assertThat(jdbc.queryForObject("select notes from weight_entries where user_id = ?", String.class, user)).isEqualTo("after lunch");
        assertThat(jdbc.queryForObject("select updated_at > created_at from weight_entries where user_id = ?", Boolean.class, user)).isTrue();
    }

    @Test
    void repeatingTheSameRequestIsSafeAndLeavesOneUnchangedRow() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);

        assertThat(put(client, TODAY, new BigDecimal("70"), "n").getResponse().getStatus()).isEqualTo(201);
        assertThat(put(client, TODAY, new BigDecimal("70"), "n").getResponse().getStatus()).isEqualTo(200);
        assertThat(put(client, TODAY, new BigDecimal("70"), "n").getResponse().getStatus()).isEqualTo(200);

        assertThat(rows(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select weight_kg from weight_entries where user_id = ?", BigDecimal.class, user)).isEqualByComparingTo("70");
    }

    @Test
    void weightKeepsTwoDecimalsExactlyAndNotesAreTrimmed() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode body = client.json(put(client, TODAY, new BigDecimal("82.35"), "  felt lighter  "));

        assertThat(body.get("weightKg").decimalValue()).isEqualByComparingTo("82.35");
        assertThat(body.get("notes").asText()).isEqualTo("felt lighter");
        assertThat(jdbc.queryForObject("select weight_kg from weight_entries", BigDecimal.class)).isEqualByComparingTo("82.35");
    }

    @Test
    void putReplacesTheWholeEntrySoOmittingOrBlankingNotesClearsThem() throws Exception {
        TestClient client = newUser("a@example.com");
        put(client, TODAY, new BigDecimal("70"), "keep me?");

        JsonNode omitted = client.json(put(client, TODAY, new BigDecimal("70.5"), null));
        assertThat(omitted.get("notes").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select notes from weight_entries", String.class)).isNull();

        put(client, TODAY, new BigDecimal("70"), "again");
        JsonNode blank = client.json(put(client, TODAY, new BigDecimal("70"), "   "));
        assertThat(blank.get("notes").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select notes from weight_entries", String.class)).isNull();
    }

    @Test
    void fieldsThatAreNotPartOfTheRequestAreIgnored() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID bobId = userId(bob);

        MvcResult result = alice.put(ENTRIES + "/" + TODAY, Map.of("weightKg", new BigDecimal("70"),
                "userId", bobId.toString(), "date", "2020-01-01", "id", UUID.randomUUID().toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(rows(bobId)).isZero();
        assertThat(jdbc.queryForObject("select entry_date::text from weight_entries where user_id = ?", String.class, userId(alice))).isEqualTo(TODAY);
    }

    // ---- validation --------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"19.99", "0", "-70", "500.01", "1000", "62.123", "62.005", "10000"})
    void rejectsInvalidWeights(String weight) throws Exception {
        TestClient client = newUser("a@example.com");

        MvcResult result = put(client, TODAY, new BigDecimal(weight), null);

        JsonNode error = expect(client, result, 400);
        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("violations").get(0).get("field").asText()).isEqualTo("weightKg");
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "20.01", "62.5", "82.35", "499.99", "500"})
    void acceptsValidWeightsIncludingTheBoundaries(String weight) throws Exception {
        TestClient client = newUser("a@example.com");
        assertThat(put(client, TODAY, new BigDecimal(weight), null).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void requiresAWeightAndRejectsNonNumbersAndOverlongNotes() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode missing = expect(client, put(client, TODAY, null, "no weight"), 400);
        assertThat(missing.get("violations").get(0).get("field").asText()).isEqualTo("weightKg");
        expect(client, put(client, TODAY, "heavy", null), 400);
        expect(client, client.put(ENTRIES + "/" + TODAY, Map.of()), 400);
        JsonNode longNote = expect(client, put(client, TODAY, new BigDecimal("70"), "n".repeat(501)), 400);
        assertThat(longNote.get("violations").get(0).get("field").asText()).isEqualTo("notes");
        assertThat(put(client, TODAY, new BigDecimal("70"), "n".repeat(500)).getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isEqualTo(1);
    }

    // ---- dates -------------------------------------------------------------------------------

    @Test
    void todayIsAllowedButTomorrowIsInTheFuture() throws Exception {
        TestClient client = newUser("a@example.com");

        assertThat(put(client, TODAY, new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);
        expectError(client, put(client, "2026-09-25", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");
        expectError(client, put(client, "2027-01-01", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isEqualTo(1);
    }

    @Test
    void datesBefore2000AreRejectedButTheFirstDayOf2000IsAllowed() throws Exception {
        TestClient client = newUser("a@example.com");

        expectError(client, put(client, "1999-12-31", new BigDecimal("70"), null), 400, "DATE_TOO_EARLY");
        expectError(client, put(client, "1985-06-15", new BigDecimal("70"), null), 400, "DATE_TOO_EARLY");
        assertThat(put(client, "2000-01-01", new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void pastDatesCanBeBackfilledAndReplaced() throws Exception {
        TestClient client = newUser("a@example.com");

        assertThat(put(client, "2026-01-15", new BigDecimal("75"), null).getResponse().getStatus()).isEqualTo(201);
        assertThat(put(client, "2026-01-15", new BigDecimal("76"), null).getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yesterday", "2026-13-45", "2026-02-30", "24-09-2026", "2026-9-4", "20260924", "not-a-date"})
    void rejectsMalformedDates(String date) throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, put(client, date, new BigDecimal("70"), null), 400, "BAD_REQUEST");
        expectError(client, client.delete(ENTRIES + "/" + date), 400, "BAD_REQUEST");
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isZero();
    }

    @Test
    void todayIsTheUsersOwnLocalDayNotTheServersDay() throws Exception {
        TestClient utc = newUser("utc@example.com", "UTC");
        TestClient kiritimati = newUser("kiri@example.com", "Pacific/Kiritimati");   // UTC+14
        TestClient losAngeles = newUser("la@example.com", "America/Los_Angeles");     // UTC-7 in September

        clock.set(Instant.parse("2026-09-24T20:00:00Z")); // UTC: the 24th. Kiritimati: already 10:00 on the 25th. LA: 13:00 on the 24th.

        assertThat(put(kiritimati, "2026-09-25", new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);
        expectError(kiritimati, put(kiritimati, "2026-09-26", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");
        expectError(utc, put(utc, "2026-09-25", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");
        assertThat(put(utc, "2026-09-24", new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);

        clock.set(Instant.parse("2026-09-24T03:00:00Z")); // LA is still on the evening of the 23rd
        expectError(losAngeles, put(losAngeles, "2026-09-24", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");
        assertThat(put(losAngeles, "2026-09-23", new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void changingTimezoneChangesWhichDaysCanBeLogged() throws Exception {
        TestClient client = newUser("a@example.com", "UTC");
        expectError(client, put(client, "2026-09-25", new BigDecimal("70"), null), 400, "DATE_IN_FUTURE");

        expect(client, client.call(HttpMethod.PATCH, "/api/v1/profile", Map.of("timezone", "Pacific/Kiritimati"), true), 200);

        // Kiritimati is 14 hours ahead, so at 10:00 UTC it is already the evening of the 25th.
        assertThat(put(client, "2026-09-25", new BigDecimal("70"), null).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- list --------------------------------------------------------------------------------

    @Test
    void listsEntriesNewestFirst() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, "2026-09-01", "80");
        log(client, "2026-09-03", "79.5");
        log(client, "2026-09-02", "79.8");

        JsonNode page = expect(client, client.get(ENTRIES), 200);

        assertThat(page.get("items").findValuesAsText("date")).containsExactly("2026-09-03", "2026-09-02", "2026-09-01");
        assertThat(page.get("items").get(0).get("weightKg").decimalValue()).isEqualByComparingTo("79.50");
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalItems").asInt()).isEqualTo(3);
    }

    @Test
    void anEmptyListIsAnEmptyPage() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode page = expect(client, client.get(ENTRIES), 200);
        assertThat(page.get("items")).isEmpty();
        assertThat(page.get("totalItems").asInt()).isZero();
        assertThat(page.get("totalPages").asInt()).isZero();
    }

    @Test
    void pagesThroughEntries() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        for (int day = 1; day <= 25; day++) insertEntry(user, String.format("2026-08-%02d", day), "70");

        JsonNode first = expect(client, client.get(ENTRIES), 200);
        JsonNode second = expect(client, client.get(ENTRIES + "?page=1"), 200);
        JsonNode small = expect(client, client.get(ENTRIES + "?size=10&page=2"), 200);

        assertThat(first.get("items")).hasSize(20);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(second.get("items")).hasSize(5);
        assertThat(second.get("items").get(0).get("date").asText()).isEqualTo("2026-08-05");
        assertThat(small.get("items")).hasSize(5);
        assertThat(small.get("items").get(0).get("date").asText()).isEqualTo("2026-08-05");
    }

    @Test
    void filtersByAnInclusiveDateRange() throws Exception {
        TestClient client = newUser("a@example.com");
        for (String day : List.of("2026-09-01", "2026-09-05", "2026-09-10", "2026-09-15")) log(client, day, "70");

        assertThat(dates(client, "?from=2026-09-05&to=2026-09-10")).containsExactly("2026-09-10", "2026-09-05");
        assertThat(dates(client, "?from=2026-09-10")).containsExactly("2026-09-15", "2026-09-10");
        assertThat(dates(client, "?to=2026-09-05")).containsExactly("2026-09-05", "2026-09-01");
        assertThat(dates(client, "?from=2026-09-05&to=2026-09-05")).containsExactly("2026-09-05");
        assertThat(dates(client, "?from=2027-01-01")).isEmpty();
    }

    private List<String> dates(TestClient client, String query) throws Exception {
        return expect(client, client.get(ENTRIES + query), 200).get("items").findValuesAsText("date");
    }

    @Test
    void rejectsAnInvertedRangeAndBadQueryParameters() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.get(ENTRIES + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
        for (String query : List.of("from=yesterday", "to=2026-13-45", "size=0", "size=101", "size=x", "page=-1")) {
            expect(client, client.get(ENTRIES + "?" + query), 400);
        }
    }

    // ---- delete ------------------------------------------------------------------------------

    @Test
    void deletesAnEntryThenReportsItMissing() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        log(client, "2026-09-01", "70");
        log(client, "2026-09-02", "71");

        expect(client, client.delete(ENTRIES + "/2026-09-01"), 204);

        assertThat(rows(user)).isEqualTo(1);
        assertThat(dates(client, "")).containsExactly("2026-09-02");
        expectError(client, client.delete(ENTRIES + "/2026-09-01"), 404, "NOT_FOUND");
        expectError(client, client.delete(ENTRIES + "/2026-09-20"), 404, "NOT_FOUND");
    }

    @Test
    void aDeletedDayCanBeLoggedAgainAsANewEntry() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, TODAY, "70");
        expect(client, client.delete(ENTRIES + "/" + TODAY), 204);

        assertThat(put(client, TODAY, new BigDecimal("71"), null).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void oldDatesCanBeDeletedEvenThoughTheyCouldNotBeCreatedNow() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        insertEntry(user, "2026-09-30", "70"); // a future-dated row, e.g. from before a timezone change

        expect(client, client.delete(ENTRIES + "/2026-09-30"), 204);
    }

    // ---- ownership ---------------------------------------------------------------------------

    @Test
    void eachUserOnlySeesAndChangesTheirOwnEntries() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID aliceId = userId(alice);
        UUID bobId = userId(bob);

        log(alice, TODAY, "60");
        // Bob logs the same day: it is his own entry, created, not an update of Alice's.
        assertThat(put(bob, TODAY, new BigDecimal("90"), null).getResponse().getStatus()).isEqualTo(201);

        assertThat(expect(alice, alice.get(ENTRIES), 200).get("items").get(0).get("weightKg").decimalValue()).isEqualByComparingTo("60");
        assertThat(expect(bob, bob.get(ENTRIES), 200).get("items").get(0).get("weightKg").decimalValue()).isEqualByComparingTo("90");
        assertThat(rows(aliceId)).isEqualTo(1);
        assertThat(rows(bobId)).isEqualTo(1);
    }

    @Test
    void anotherUsersDayLooksLikeItDoesNotExist() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        log(alice, "2026-09-01", "60");

        expectError(bob, bob.delete(ENTRIES + "/2026-09-01"), 404, "NOT_FOUND");

        assertThat(rows(userId(alice))).isEqualTo(1);
        assertThat(expect(bob, bob.get(ENTRIES), 200).get("items")).isEmpty();
    }

    // ---- access ------------------------------------------------------------------------------

    @Test
    void everyEndpointRequiresAnAuthenticatedSession() throws Exception {
        TestClient anonymous = new TestClient(mvc, mapper);
        expectError(anonymous, anonymous.get(ENTRIES), 401, "UNAUTHENTICATED");
        expectError(anonymous, put(anonymous, TODAY, new BigDecimal("70"), null), 401, "UNAUTHENTICATED");
        expectError(anonymous, anonymous.delete(ENTRIES + "/" + TODAY), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isZero();
    }

    @Test
    void changesRequireACsrfToken() throws Exception {
        TestClient client = newUser("a@example.com");
        log(client, TODAY, "70");

        expectError(client, client.call(HttpMethod.PUT, ENTRIES + "/2026-09-01", Map.of("weightKg", new BigDecimal("70")), false), 403, "CSRF_TOKEN_INVALID");
        expectError(client, client.call(HttpMethod.DELETE, ENTRIES + "/" + TODAY, null, false), 403, "CSRF_TOKEN_INVALID");

        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isEqualTo(1);
        assertThat(client.get(ENTRIES).getResponse().getStatus()).isEqualTo(200); // reads need no token
    }
}
