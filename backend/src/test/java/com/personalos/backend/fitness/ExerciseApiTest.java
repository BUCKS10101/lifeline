package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExerciseApiTest extends FitnessApiTest {

    private static final String EXERCISES = "/api/v1/exercises";

    private UUID createCustom(TestClient client, String name) throws Exception {
        JsonNode body = expect(client, client.post(EXERCISES, Map.of("name", name, "primaryMuscleGroup", "BACK")), 201);
        return UUID.fromString(body.get("id").asText());
    }

    // ---- listing -----------------------------------------------------------------------------

    @Test
    void listsBuiltInExercisesSortedByNameWithDefaultPaging() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode page = expect(client, client.get(EXERCISES), 200);

        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalItems").asInt()).isGreaterThanOrEqualTo(40);
        assertThat(page.get("items")).hasSize(20);
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("builtIn").asBoolean()).isTrue();
        assertThat(first.get("archived").asBoolean()).isFalse();
        List<String> names = page.get("items").findValuesAsText("name");
        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void filtersBySearchTextAndMuscleGroup() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode bench = expect(client, client.get(EXERCISES + "?q=BENCH&size=100"), 200);
        assertThat(bench.get("items").findValuesAsText("name")).isNotEmpty().allMatch(n -> n.toLowerCase().contains("bench"));

        JsonNode chest = expect(client, client.get(EXERCISES + "?muscleGroup=CHEST&size=100"), 200);
        assertThat(chest.get("items").findValuesAsText("primaryMuscleGroup")).isNotEmpty().containsOnly("CHEST");

        JsonNode none = expect(client, client.get(EXERCISES + "?q=zzzzzz"), 200);
        assertThat(none.get("items")).isEmpty();
        assertThat(none.get("totalItems").asInt()).isZero();
    }

    @Test
    void searchTreatsPercentAndUnderscoreLiterally() throws Exception {
        TestClient client = newUser("a@example.com");
        assertThat(expect(client, client.get(EXERCISES + "?q=%25"), 200).get("items")).isEmpty();
        assertThat(expect(client, client.get(EXERCISES + "?q=_"), 200).get("items")).isEmpty();
    }

    @Test
    void pagesThroughTheCatalogue() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode first = expect(client, client.get(EXERCISES + "?size=10&page=0"), 200);
        JsonNode second = expect(client, client.get(EXERCISES + "?size=10&page=1"), 200);
        assertThat(first.get("items").findValuesAsText("id")).doesNotContainAnyElementsOf(second.get("items").findValuesAsText("id"));
        assertThat(first.get("totalPages").asInt()).isGreaterThanOrEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "size=-1", "page=-1", "muscleGroup=WINGS", "size=abc", "includeArchived=maybe"})
    void rejectsInvalidQueryParameters(String query) throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode body = expect(client, client.get(EXERCISES + "?" + query), 400);
        assertThat(body.get("code").asText()).isIn("VALIDATION_FAILED", "BAD_REQUEST");
    }

    @Test
    void sizeViolationNamesTheParameter() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode body = expect(client, client.get(EXERCISES + "?size=500"), 400);
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("violations").get(0).get("field").asText()).isEqualTo("size");
    }

    // ---- create ------------------------------------------------------------------------------

    @Test
    void createsACustomExerciseVisibleOnlyToItsOwner() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");

        JsonNode created = expect(alice, alice.post(EXERCISES,
                Map.of("name", "  Zercher Squat ", "primaryMuscleGroup", "QUADS", "equipment", "BARBELL")), 201);

        assertThat(created.get("name").asText()).isEqualTo("Zercher Squat");
        assertThat(created.get("builtIn").asBoolean()).isFalse();
        assertThat(created.get("equipment").asText()).isEqualTo("BARBELL");
        assertThat(expect(alice, alice.get(EXERCISES + "?q=zercher"), 200).get("items")).hasSize(1);
        assertThat(expect(bob, bob.get(EXERCISES + "?q=zercher"), 200).get("items")).isEmpty();
    }

    @Test
    void rejectsDuplicateNamesAgainstOwnAndBuiltInExercisesIgnoringCase() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        createCustom(alice, "Cable Row Variant");

        expectError(alice, alice.post(EXERCISES, Map.of("name", "cable row VARIANT", "primaryMuscleGroup", "BACK")),
                409, "DUPLICATE_EXERCISE");
        expectError(alice, alice.post(EXERCISES, Map.of("name", "barbell bench press", "primaryMuscleGroup", "CHEST")),
                409, "DUPLICATE_EXERCISE");
        createCustom(bob, "Cable Row Variant"); // another user may use the same name
    }

    @Test
    void validatesCreateRequests() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode blank = expect(client, client.post(EXERCISES, Map.of("name", "   ", "primaryMuscleGroup", "BACK")), 400);
        assertThat(blank.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(blank.get("violations").get(0).get("field").asText()).isEqualTo("name");

        expect(client, client.post(EXERCISES, Map.of("name", "x".repeat(101), "primaryMuscleGroup", "BACK")), 400);
        expect(client, client.post(EXERCISES, Map.of("name", "Fine", "primaryMuscleGroup", "BACK", "equipment", "e".repeat(31))), 400);
        expect(client, client.post(EXERCISES, Map.of("name", "Fine")), 400);
        expect(client, client.post(EXERCISES, Map.of("name", "Fine", "primaryMuscleGroup", "WINGS")), 400);
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id is not null", Integer.class)).isZero();
    }

    // ---- update ------------------------------------------------------------------------------

    @Test
    void updatesOwnExercisePartially() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID id = createCustom(client, "Original");

        JsonNode renamed = expect(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of("name", " Renamed "), true), 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Renamed");
        assertThat(renamed.get("primaryMuscleGroup").asText()).isEqualTo("BACK");

        JsonNode regrouped = expect(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id,
                Map.of("primaryMuscleGroup", "BICEPS", "equipment", "CABLE"), true), 200);
        assertThat(regrouped.get("name").asText()).isEqualTo("Renamed");
        assertThat(regrouped.get("primaryMuscleGroup").asText()).isEqualTo("BICEPS");
        assertThat(regrouped.get("equipment").asText()).isEqualTo("CABLE");

        JsonNode cleared = expect(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of("equipment", " "), true), 200);
        assertThat(cleared.get("equipment").isNull()).isTrue();
    }

    @Test
    void renamingToAnExistingNameIsRejectedButKeepingYourOwnNameIsFine() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID first = createCustom(client, "First");
        createCustom(client, "Second");

        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + first, Map.of("name", "second"), true), 409, "DUPLICATE_EXERCISE");
        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + first, Map.of("name", "Barbell Bench Press"), true), 409, "DUPLICATE_EXERCISE");
        expect(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + first, Map.of("name", "FIRST"), true), 200);
    }

    @Test
    void builtInExercisesAreReadOnly() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");

        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + bench, Map.of("name", "Hacked"), true), 403, "BUILTIN_READ_ONLY");
        expectError(client, client.delete(EXERCISES + "/" + bench), 403, "BUILTIN_READ_ONLY");
        assertThat(jdbc.queryForObject("select name from exercises where id = ?", String.class, bench)).isEqualTo("Barbell Bench Press");
    }

    @Test
    void updateRejectsEmptyBodyAndInvalidValues() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID id = createCustom(client, "Thing");
        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of(), true), 400, "EMPTY_UPDATE");
        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of("name", "   "), true), 400, "VALIDATION_FAILED");
        expect(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of("primaryMuscleGroup", "WINGS"), true), 400);
    }

    // ---- delete and archive ------------------------------------------------------------------

    @Test
    void deletesAnUnusedExerciseOutright() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID id = createCustom(client, "Unused");

        expect(client, client.delete(EXERCISES + "/" + id), 204);

        assertThat(jdbc.queryForObject("select count(*) from exercises where id = ?", Integer.class, id)).isZero();
        expectError(client, client.delete(EXERCISES + "/" + id), 404, "NOT_FOUND");
    }

    @Test
    void archivesAnExerciseThatAppearsInHistoryInsteadOfDeletingIt() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID id = createCustom(client, "Used In History");
        addExerciseWithSets(insertCompletedWorkout(user, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z")), id, 1, s("50", 8));

        expect(client, client.delete(EXERCISES + "/" + id), 204);

        assertThat(jdbc.queryForObject("select archived_at is not null from exercises where id = ?", Boolean.class, id)).isTrue();
        assertThat(expect(client, client.get(EXERCISES + "?q=history"), 200).get("items")).isEmpty();
        JsonNode withArchived = expect(client, client.get(EXERCISES + "?q=history&includeArchived=true"), 200);
        assertThat(withArchived.get("items")).hasSize(1);
        assertThat(withArchived.get("items").get(0).get("archived").asBoolean()).isTrue();
        // History stays readable, and deleting again is harmless.
        expect(client, client.get(EXERCISES + "/" + id + "/history"), 200);
        expect(client, client.delete(EXERCISES + "/" + id), 204);
        // An archived exercise cannot be edited.
        expectError(client, client.call(HttpMethod.PATCH, EXERCISES + "/" + id, Map.of("name", "New"), true), 409, "EXERCISE_ARCHIVED");
    }

    @Test
    void archivesAnExerciseThatAppearsInATemplate() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID id = createCustom(client, "In Template");
        UUID template = UUID.randomUUID();
        jdbc.update("insert into workout_templates (id, owner_id, name) values (?, ?, 'T')", template, user);
        jdbc.update("insert into workout_template_exercises (id, template_id, exercise_id, position) values (?, ?, ?, 1)",
                UUID.randomUUID(), template, id);

        expect(client, client.delete(EXERCISES + "/" + id), 204);

        assertThat(jdbc.queryForObject("select archived_at is not null from exercises where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void anArchivedExerciseFreesItsNameForReuse() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID id = createCustom(client, "Reusable");
        addExerciseWithSets(insertCompletedWorkout(user, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z")), id, 1, s("50", 8));
        expect(client, client.delete(EXERCISES + "/" + id), 204);

        UUID again = createCustom(client, "Reusable");

        assertThat(again).isNotEqualTo(id);
    }

    // ---- history -----------------------------------------------------------------------------

    @Test
    void historyIsEmptyForAnExerciseNeverDone() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode history = expect(client, client.get(EXERCISES + "/" + builtInExercise("Deadlift") + "/history"), 200);
        assertThat(history.get("items")).isEmpty();
        assertThat(history.get("totalItems").asInt()).isZero();
    }

    @Test
    void historyListsCompletedWorkoutsNewestFirstWithTopSetAndVolume() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID older = insertCompletedWorkout(user, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z"));
        addExerciseWithSets(older, bench, 1, w("40", 10), s("60", 8), s("60", 6));
        UUID newer = insertCompletedWorkout(user, LocalDate.of(2026, 9, 8), Instant.parse("2026-09-08T10:00:00Z"));
        addExerciseWithSets(newer, bench, 1, s("62.5", 8), s("65", 5));
        insertInProgressWorkout(user, LocalDate.of(2026, 9, 9), Instant.parse("2026-09-09T10:00:00Z"));

        JsonNode history = expect(client, client.get(EXERCISES + "/" + bench + "/history"), 200);

        assertThat(history.get("items")).hasSize(2);
        JsonNode first = history.get("items").get(0);
        assertThat(first.get("workoutId").asText()).isEqualTo(newer.toString());
        assertThat(first.get("performedOn").asText()).isEqualTo("2026-09-08");
        assertThat(first.get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("65");
        assertThat(first.get("volumeKg").decimalValue()).isEqualByComparingTo("825"); // 62.5*8 + 65*5

        JsonNode second = history.get("items").get(1);
        assertThat(second.get("sets")).hasSize(3);
        assertThat(second.get("sets").get(0).get("warmup").asBoolean()).isTrue();
        assertThat(second.get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("60");
        assertThat(second.get("topSet").get("reps").asInt()).isEqualTo(8);
        assertThat(second.get("volumeKg").decimalValue()).isEqualByComparingTo("840"); // warm-up excluded: 60*8 + 60*6
    }

    @Test
    void historyPages() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID squat = builtInExercise("Barbell Back Squat");
        for (int day = 1; day <= 5; day++) {
            UUID workout = insertCompletedWorkout(user, LocalDate.of(2026, 9, day), Instant.parse("2026-09-0" + day + "T10:00:00Z"));
            addExerciseWithSets(workout, squat, 1, s("100", 5));
        }
        JsonNode page = expect(client, client.get(EXERCISES + "/" + squat + "/history?size=2&page=1"), 200);
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("totalItems").asInt()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("items").get(0).get("performedOn").asText()).isEqualTo("2026-09-03");
    }

    @Test
    void historyNeverIncludesAnotherUsersWorkouts() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        addExerciseWithSets(insertCompletedWorkout(userId(alice), LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z")),
                bench, 1, s("100", 5));

        assertThat(expect(alice, alice.get(EXERCISES + "/" + bench + "/history"), 200).get("items")).hasSize(1);
        assertThat(expect(bob, bob.get(EXERCISES + "/" + bench + "/history"), 200).get("items")).isEmpty();
    }

    // ---- records -----------------------------------------------------------------------------

    @Test
    void recordsAreEmptyWithNoHistoryAndIncludeTheExercise() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");

        JsonNode records = expect(client, client.get(EXERCISES + "/" + bench + "/records"), 200);

        assertThat(records.get("exercise").get("name").asText()).isEqualTo("Barbell Bench Press");
        assertThat(records.get("heaviestWeight").isNull()).isTrue();
        assertThat(records.get("bestEstimated1rm").isNull()).isTrue();
        assertThat(records.get("bestRepsAtWeight")).isEmpty();
    }

    @Test
    void recordsReportBestsAcrossCompletedWorkoutsIgnoringWarmUpsAndInProgressWork() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID bench = builtInExercise("Barbell Bench Press");
        addExerciseWithSets(insertCompletedWorkout(user, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z")),
                bench, 1, w("150", 3), s("100", 5), s("90", 10));
        addExerciseWithSets(insertCompletedWorkout(user, LocalDate.of(2026, 9, 8), Instant.parse("2026-09-08T10:00:00Z")),
                bench, 1, s("105", 3));
        addExerciseWithSets(insertInProgressWorkout(user, LocalDate.of(2026, 9, 9), Instant.parse("2026-09-09T10:00:00Z")),
                bench, 1, s("200", 1));

        JsonNode records = expect(client, client.get(EXERCISES + "/" + bench + "/records"), 200);

        assertThat(records.get("heaviestWeight").get("weightKg").decimalValue()).isEqualByComparingTo("105");
        assertThat(records.get("heaviestWeight").get("performedOn").asText()).isEqualTo("2026-09-08");
        JsonNode e1rm = records.get("bestEstimated1rm");
        assertThat(e1rm.get("valueKg").decimalValue()).isEqualByComparingTo("120.00"); // 90 x 10
        assertThat(e1rm.get("weightKg").decimalValue()).isEqualByComparingTo("90");
        List<Integer> weights = records.get("bestRepsAtWeight").findValues("weightKg").stream().map(JsonNode::asInt).toList();
        assertThat(weights).containsExactly(105, 100, 90);
    }

    // ---- ownership and access ----------------------------------------------------------------

    @Test
    void anotherUsersCustomExerciseLooksLikeItDoesNotExist() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID secret = createCustom(alice, "Alice Only");

        expectError(bob, bob.call(HttpMethod.PATCH, EXERCISES + "/" + secret, Map.of("name", "Mine now"), true), 404, "NOT_FOUND");
        expectError(bob, bob.delete(EXERCISES + "/" + secret), 404, "NOT_FOUND");
        expectError(bob, bob.get(EXERCISES + "/" + secret + "/history"), 404, "NOT_FOUND");
        expectError(bob, bob.get(EXERCISES + "/" + secret + "/records"), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select name from exercises where id = ?", String.class, secret)).isEqualTo("Alice Only");
    }

    @Test
    void unknownAndMalformedIdsAreDistinguished() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.get(EXERCISES + "/" + UUID.randomUUID() + "/records"), 404, "NOT_FOUND");
        expectError(client, client.get(EXERCISES + "/" + UUID.randomUUID() + "/history"), 404, "NOT_FOUND");
        expectError(client, client.delete(EXERCISES + "/" + UUID.randomUUID()), 404, "NOT_FOUND");
        expect(client, client.get(EXERCISES + "/not-a-uuid/records"), 400);
        expect(client, client.delete(EXERCISES + "/not-a-uuid"), 400);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        TestClient anonymous = new TestClient(mvc, mapper);
        expectError(anonymous, anonymous.get(EXERCISES), 401, "UNAUTHENTICATED");
        expectError(anonymous, anonymous.get(EXERCISES + "/" + UUID.randomUUID() + "/history"), 401, "UNAUTHENTICATED");
        expectError(anonymous, anonymous.post(EXERCISES, Map.of("name", "X", "primaryMuscleGroup", "BACK")), 401, "UNAUTHENTICATED");

        TestClient client = newUser("a@example.com");
        expectError(client, client.call(HttpMethod.POST, EXERCISES, Map.of("name", "X", "primaryMuscleGroup", "BACK"), false),
                403, "CSRF_TOKEN_INVALID");
        expectError(client, client.call(HttpMethod.DELETE, EXERCISES + "/" + UUID.randomUUID(), null, false), 403, "CSRF_TOKEN_INVALID");
    }
}
