package com.personalos.backend.wellness.protein;

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
class ProteinApiTest extends ApiTestBase {

    private static final String PROTEIN = "/api/v1/protein-entries";
    private static final String SUGGESTIONS = "/api/v1/protein/suggestions";
    private static final String PREFS = "/api/v1/wellness/preferences";
    private static final String TODAY = "2026-09-24";

    private MvcResult add(TestClient c, Object grams, String label, String date, UUID id) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (grams != null) body.put("grams", grams);
        if (label != null) body.put("label", label);
        if (date != null) body.put("date", date);
        if (id != null) body.put("id", id.toString());
        return c.post(PROTEIN, body);
    }

    private JsonNode added(TestClient c, int grams, String label) throws Exception {
        return expect(c, add(c, grams, label, null, null), 201);
    }

    /** Adds one minute later than the previous one, so "most recent" is unambiguous. */
    private void addLater(TestClient c, int grams, String label) throws Exception {
        clock.advance(Duration.ofMinutes(1));
        added(c, grams, label);
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from protein_entries where user_id = ?", Integer.class, user);
    }

    private void goal(TestClient c, Integer grams) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("sleepEnabled", true, "waterEnabled", true, "proteinEnabled", true));
        if (grams != null) body.put("proteinGoalG", grams);
        expect(c, c.put(PREFS, body), 200);
    }

    private List<String> labels(JsonNode suggestions) {
        List<String> out = new ArrayList<>();
        suggestions.forEach(s -> out.add(s.get("label").asText()));
        return out;
    }

    // ---- adding ------------------------------------------------------------------------------

    @Test
    void entriesAreIndividualWithARunningTotalAndAnOptionalLabel() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode first = added(c, 25, "Whey shake");
        assertThat(first.get("entry").get("grams").asInt()).isEqualTo(25);
        assertThat(first.get("entry").get("label").asText()).isEqualTo("Whey shake");
        assertThat(first.get("entry").get("date").asText()).isEqualTo(TODAY);
        assertThat(first.get("totalG").asInt()).isEqualTo(25);

        clock.advance(Duration.ofMinutes(1));
        JsonNode second = added(c, 20, null);
        assertThat(second.get("entry").get("label").isNull()).isTrue();
        assertThat(second.get("totalG").asInt()).isEqualTo(45);

        JsonNode day = expect(c, c.get(PROTEIN), 200);
        assertThat(day.get("totalG").asInt()).isEqualTo(45);
        assertThat(day.get("entries")).hasSize(2);
        assertThat(day.get("entries").get(0).get("grams").asInt()).isEqualTo(20);   // newest first
        assertThat(rows(userId(c))).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 500})
    void gramsInsideTheRangeAreAccepted(int grams) throws Exception {
        TestClient c = newUser("a@example.com");
        assertThat(added(c, grams, null).get("totalG").asInt()).isEqualTo(grams);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 501, 10000})
    void gramsOutsideTheRangeAreRejectedAndNothingIsSaved(int grams) throws Exception {
        TestClient c = newUser("a@example.com");
        MvcResult result = add(c, grams, null, null, null);
        expectError(c, result, 400, "VALIDATION_FAILED");
        assertThat(c.json(result).get("violations").toString()).contains("grams");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void gramsAreRequired() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, add(c, null, "Whey", null, null), 400, "VALIDATION_FAILED");
        expect(c, add(c, "lots", null, null, null), 400);
    }

    @Test
    void labelsAreTrimmedBlankOnesAreNoneAndTheLimitIsSixtyCharacters() throws Exception {
        TestClient c = newUser("a@example.com");
        assertThat(added(c, 20, "  Whey shake  ").get("entry").get("label").asText()).isEqualTo("Whey shake");
        assertThat(added(c, 20, "   ").get("entry").get("label").isNull()).isTrue();
        assertThat(added(c, 20, "").get("entry").get("label").isNull()).isTrue();
        assertThat(added(c, 20, "Chicken Breast").get("entry").get("label").asText()).isEqualTo("Chicken Breast");   // case kept
        assertThat(added(c, 20, "x".repeat(60)).get("entry").get("label").asText()).hasSize(60);
        MvcResult tooLong = add(c, 20, "x".repeat(61), null, null);
        expectError(c, tooLong, 400, "VALIDATION_FAILED");
        assertThat(c.json(tooLong).get("violations").toString()).contains("label");
        assertThat(rows(userId(c))).isEqualTo(5);
    }

    @Test
    void todayFollowsTheTimezoneAndBackDatingWorksAndBadDatesAreRejected() throws Exception {
        TestClient kiritimati = newUser("k@example.com", "Pacific/Kiritimati");
        assertThat(added(kiritimati, 20, null).get("entry").get("date").asText()).isEqualTo("2026-09-25");

        TestClient c = newUser("a@example.com");
        JsonNode yesterday = expect(c, add(c, 30, null, "2026-09-23", null), 201);
        assertThat(yesterday.get("totalG").asInt()).isEqualTo(30);
        assertThat(expect(c, c.get(PROTEIN), 200).get("totalG").asInt()).isZero();
        assertThat(expect(c, c.get(PROTEIN + "?date=2026-09-23"), 200).get("totalG").asInt()).isEqualTo(30);
        expectError(c, add(c, 20, null, "2026-09-25", null), 400, "DATE_IN_FUTURE");
        expectError(c, add(c, 20, null, "1999-12-31", null), 400, "DATE_TOO_EARLY");
        expect(c, add(c, 20, null, "nope", null), 400);
    }

    // ---- client ids --------------------------------------------------------------------------

    @Test
    void repeatingTheSameClientIdReturnsTheExistingEntryAndCountsItOnce() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID id = UUID.randomUUID();
        assertThat(add(c, 25, "Whey", null, id).getResponse().getStatus()).isEqualTo(201);
        MvcResult retry = add(c, 99, "Different", null, id);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(c.json(retry).get("entry").get("grams").asInt()).isEqualTo(25);     // the first one won
        assertThat(c.json(retry).get("entry").get("label").asText()).isEqualTo("Whey");
        assertThat(c.json(retry).get("totalG").asInt()).isEqualTo(25);
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aClientIdBelongingToSomeoneElseIsRefusedWithoutTouchingTheirEntry() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID id = UUID.randomUUID();
        add(a, 25, "Whey", null, id);
        expectError(b, add(b, 99, null, null, id), 409, "CONFLICT");
        assertThat(rows(userId(b))).isZero();
        assertThat(jdbc.queryForObject("select grams from protein_entries where id = ?", Integer.class, id)).isEqualTo(25);
        expect(a, a.post(PROTEIN, Map.of("grams", 20, "id", "not-a-uuid")), 400);
    }

    // ---- undo, day view and goal -------------------------------------------------------------

    @Test
    void deleteRemovesTheEntryAndAnotherUsersEntryCannotBeDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String keep = added(a, 25, null).get("entry").get("id").asText();
        clock.advance(Duration.ofMinutes(1));
        String undo = added(a, 30, null).get("entry").get("id").asText();

        expectError(b, b.delete(PROTEIN + "/" + undo), 404, "NOT_FOUND");
        expect(a, a.delete(PROTEIN + "/" + undo), 204);
        expectError(a, a.delete(PROTEIN + "/" + undo), 404, "NOT_FOUND");
        JsonNode day = expect(a, a.get(PROTEIN), 200);
        assertThat(day.get("totalG").asInt()).isEqualTo(25);
        assertThat(day.get("entries").get(0).get("id").asText()).isEqualTo(keep);
        expect(a, a.delete(PROTEIN + "/not-a-uuid"), 400);
    }

    @Test
    void anEmptyDayAndAGoalShowTotalsAndProgressWithoutAnyVerdict() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode empty = expect(c, c.get(PROTEIN), 200);
        assertThat(empty.get("totalG").asInt()).isZero();
        assertThat(empty.get("entries")).isEmpty();
        assertThat(empty.get("goalG").isNull()).isTrue();
        assertThat(empty.get("progressPercent").isNull()).isTrue();

        goal(c, 140);
        added(c, 82, null);
        JsonNode partway = expect(c, c.get(PROTEIN), 200);
        assertThat(partway.get("goalG").asInt()).isEqualTo(140);
        assertThat(partway.get("progressPercent").asInt()).isEqualTo(58);
        assertThat(partway.get("goalReached").asBoolean()).isFalse();

        added(c, 60, null);
        JsonNode over = expect(c, c.get(PROTEIN), 200);
        assertThat(over.get("totalG").asInt()).isEqualTo(142);
        assertThat(over.get("progressPercent").asInt()).isEqualTo(100);
        assertThat(over.get("goalReached").asBoolean()).isTrue();
    }

    @Test
    void aDaysTotalOnlyCountsThatDayAndThatPerson() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        added(a, 25, null);
        expect(a, add(a, 40, null, "2026-09-23", null), 201);
        added(b, 50, null);
        assertThat(expect(a, a.get(PROTEIN), 200).get("totalG").asInt()).isEqualTo(25);
        assertThat(expect(b, b.get(PROTEIN), 200).get("totalG").asInt()).isEqualTo(50);
    }

    // ---- suggestions -------------------------------------------------------------------------

    @Test
    void suggestionsAreTheUsersOwnLabelsMostUsedFirstThenMostRecent() throws Exception {
        TestClient c = newUser("a@example.com");
        addLater(c, 25, "Whey shake");
        addLater(c, 40, "Chicken breast");
        addLater(c, 30, "whey shake");        // the same label in another case: one label, shown as last typed
        addLater(c, 15, "Greek yogurt");
        addLater(c, 12, "Eggs");
        addLater(c, 45, "Chicken breast");
        addLater(c, 20, null);                // no label: never a suggestion

        JsonNode s = expect(c, c.get(SUGGESTIONS), 200);
        assertThat(labels(s)).containsExactly("Chicken breast", "whey shake", "Eggs", "Greek yogurt");
        // Two uses each: chicken was used last, so it comes first. One use each: eggs was used after yogurt.
        assertThat(s.get(0).get("grams").asInt()).isEqualTo(45);   // the grams of the last use
        assertThat(s.get(0).get("uses").asInt()).isEqualTo(2);
        assertThat(s.get(1).get("grams").asInt()).isEqualTo(30);
        assertThat(s.get(1).get("uses").asInt()).isEqualTo(2);
        assertThat(s.get(2).get("uses").asInt()).isEqualTo(1);
    }

    @Test
    void labelsUsedAtTheSameInstantAreOrderedByNameSoTheOrderIsAlwaysTheSame() throws Exception {
        TestClient c = newUser("a@example.com");
        added(c, 20, "Bananas");
        added(c, 20, "Almonds");
        JsonNode first = expect(c, c.get(SUGGESTIONS), 200);
        assertThat(labels(first)).containsExactly("Almonds", "Bananas");
        assertThat(expect(c, c.get(SUGGESTIONS), 200)).isEqualTo(first);
    }

    @Test
    void suggestionsAreCappedAtSix() throws Exception {
        TestClient c = newUser("a@example.com");
        for (int i = 1; i <= 8; i++) addLater(c, 10 + i, "Food " + i);
        JsonNode s = expect(c, c.get(SUGGESTIONS), 200);
        assertThat(s).hasSize(6);
        assertThat(labels(s).get(0)).isEqualTo("Food 8");   // all used once: the most recent first
    }

    @Test
    void suggestionsNeverIncludeAnotherUsersLabelsAndAreEmptyForANewUser() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        addLater(a, 25, "Secret shake");
        addLater(a, 25, "Secret shake");
        assertThat(expect(b, b.get(SUGGESTIONS), 200)).isEmpty();
        addLater(b, 30, "Tofu");
        assertThat(labels(expect(b, b.get(SUGGESTIONS), 200))).containsExactly("Tofu");
        assertThat(labels(expect(a, a.get(SUGGESTIONS), 200))).containsExactly("Secret shake");
    }

    @Test
    void undoingAnEntryUpdatesTheSuggestions() throws Exception {
        TestClient c = newUser("a@example.com");
        addLater(c, 40, "Chicken breast");
        clock.advance(Duration.ofMinutes(1));
        String last = added(c, 45, "Chicken breast").get("entry").get("id").asText();
        assertThat(expect(c, c.get(SUGGESTIONS), 200).get(0).get("grams").asInt()).isEqualTo(45);

        expect(c, c.delete(PROTEIN + "/" + last), 204);
        JsonNode after = expect(c, c.get(SUGGESTIONS), 200);
        assertThat(after.get(0).get("grams").asInt()).isEqualTo(40);
        assertThat(after.get(0).get("uses").asInt()).isEqualTo(1);
    }

    // ---- access ------------------------------------------------------------------------------

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        assertThat(anon.get(PROTEIN).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.get(SUGGESTIONS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(PROTEIN, Map.of("grams", 20)).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(PROTEIN + "/" + UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        assertThat(c.call(HttpMethod.POST, PROTEIN, Map.of("grams", 20), false).getResponse().getStatus()).isEqualTo(403);
        String id = added(c, 20, null).get("entry").get("id").asText();
        assertThat(c.call(HttpMethod.DELETE, PROTEIN + "/" + id, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
