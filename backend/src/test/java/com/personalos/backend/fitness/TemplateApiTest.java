package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateApiTest extends FitnessApiTest {

    private static final String TEMPLATES = "/api/v1/workout-templates";

    private Map<String, Object> request(String name, Object... exerciseIds) {
        List<Map<String, Object>> exercises = new ArrayList<>();
        for (Object id : exerciseIds) exercises.add(Map.of("exerciseId", id.toString()));
        return Map.of("name", name, "exercises", exercises);
    }

    private UUID create(TestClient client, String name, UUID... exerciseIds) throws Exception {
        return UUID.fromString(expect(client, client.post(TEMPLATES, request(name, (Object[]) exerciseIds)), 201).get("id").asText());
    }

    private List<String> exerciseNames(JsonNode detail) {
        return detail.get("exercises").findValues("exercise").stream().map(e -> e.get("name").asText()).toList();
    }

    // ---- built-in templates ------------------------------------------------------------------

    @Test
    void listsPushPullAndLegsBuiltInsWithExerciseCounts() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode page = expect(client, client.get(TEMPLATES), 200);

        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode item : page.get("items")) {
            assertThat(item.get("builtIn").asBoolean()).isTrue();
            counts.put(item.get("name").asText(), item.get("exerciseCount").asInt());
        }
        assertThat(counts).containsEntry("Push", 6).containsEntry("Pull", 7).containsEntry("Legs", 6);
        assertThat(page.get("items").findValuesAsText("name")).containsExactly("Legs", "Pull", "Push");
    }

    @Test
    void builtInDetailListsExercisesInOrderWithTargetSets() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode push = expect(client, client.get(TEMPLATES + "/" + builtInTemplate("Push")), 200);

        assertThat(push.get("builtIn").asBoolean()).isTrue();
        assertThat(exerciseNames(push)).containsExactly("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press",
                "Lateral Raise", "Triceps Pushdown", "Overhead Triceps Extension");
        assertThat(push.get("exercises").findValues("position").stream().map(JsonNode::asInt)).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(push.get("exercises").get(0).get("targetSets").asInt()).isEqualTo(4);
        assertThat(push.get("exercises").get(0).get("exercise").get("builtIn").asBoolean()).isTrue();
    }

    @Test
    void builtInTemplatesAreReadOnly() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID push = builtInTemplate("Push");

        expectError(client, client.put(TEMPLATES + "/" + push, request("Hacked", builtInExercise("Deadlift"))), 403, "BUILTIN_READ_ONLY");
        expectError(client, client.delete(TEMPLATES + "/" + push), 403, "BUILTIN_READ_ONLY");
        assertThat(jdbc.queryForObject("select name from workout_templates where id = ?", String.class, push)).isEqualTo("Push");
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises where template_id = ?", Integer.class, push)).isEqualTo(6);
    }

    // ---- create ------------------------------------------------------------------------------

    @Test
    void createsATemplateVisibleOnlyToItsOwner() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID squat = builtInExercise("Barbell Back Squat");

        JsonNode created = expect(alice, alice.post(TEMPLATES, Map.of(
                "name", "  My Day ", "notes", "Heavy",
                "exercises", List.of(Map.of("exerciseId", squat.toString(), "targetSets", 5), Map.of("exerciseId", bench.toString())))), 201);

        assertThat(created.get("name").asText()).isEqualTo("My Day");
        assertThat(created.get("builtIn").asBoolean()).isFalse();
        assertThat(created.get("notes").asText()).isEqualTo("Heavy");
        assertThat(exerciseNames(created)).containsExactly("Barbell Back Squat", "Barbell Bench Press");
        assertThat(created.get("exercises").get(0).get("targetSets").asInt()).isEqualTo(5);
        assertThat(created.get("exercises").get(1).get("targetSets").isNull()).isTrue();

        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(expect(alice, alice.get(TEMPLATES + "?size=100"), 200).get("items").findValuesAsText("id")).contains(id.toString());
        assertThat(expect(bob, bob.get(TEMPLATES + "?size=100"), 200).get("items").findValuesAsText("id")).doesNotContain(id.toString());
    }

    @Test
    void mayReferenceOwnCustomExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode exercise = expect(client, client.post("/api/v1/exercises", Map.of("name", "Mine", "primaryMuscleGroup", "BACK")), 201);
        UUID custom = UUID.fromString(exercise.get("id").asText());

        JsonNode created = expect(client, client.post(TEMPLATES, request("With Custom", custom)), 201);

        assertThat(exerciseNames(created)).containsExactly("Mine");
    }

    @Test
    void validatesCreateRequests() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");

        expectError(client, client.post(TEMPLATES, request("   ", bench)), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, request("x".repeat(101), bench)), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T", "exercises", List.of())), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T")), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T", "notes", "n".repeat(1001),
                "exercises", List.of(Map.of("exerciseId", bench.toString())))), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T",
                "exercises", List.of(Map.of("exerciseId", bench.toString(), "targetSets", 0)))), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T",
                "exercises", List.of(Map.of("exerciseId", bench.toString(), "targetSets", 21)))), 400, "VALIDATION_FAILED");
        expectError(client, client.post(TEMPLATES, Map.of("name", "T", "exercises", List.of(Map.of("targetSets", 3)))), 400, "VALIDATION_FAILED");
        expect(client, client.post(TEMPLATES, Map.of("name", "T", "exercises", List.of(Map.of("exerciseId", "nope")))), 400);
        assertThat(jdbc.queryForObject("select count(*) from workout_templates where owner_id is not null", Integer.class)).isZero();
    }

    @Test
    void limitsATemplateToThirtyExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        List<UUID> ids = jdbc.queryForList("select id from exercises where owner_id is null limit 31", UUID.class);
        assertThat(ids).hasSize(31);

        expectError(client, client.post(TEMPLATES, request("Too big", ids.toArray())), 400, "VALIDATION_FAILED");
        expect(client, client.post(TEMPLATES, request("Just right", ids.subList(0, 30).toArray())), 201);
    }

    @Test
    void rejectsDuplicateUnknownForeignAndArchivedExercises() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");

        expectError(alice, alice.post(TEMPLATES, request("Dup", bench, bench)), 400, "DUPLICATE_TEMPLATE_EXERCISE");
        expectError(alice, alice.post(TEMPLATES, request("Unknown", UUID.randomUUID())), 404, "NOT_FOUND");

        UUID alicesCustom = insertExercise(userId(alice), "Alice Only");
        expectError(bob, bob.post(TEMPLATES, request("Steal", alicesCustom)), 404, "NOT_FOUND");

        UUID archived = insertExercise(userId(alice), "Old");
        jdbc.update("update exercises set archived_at = now() where id = ?", archived);
        expectError(alice, alice.post(TEMPLATES, request("Archived", bench, archived)), 409, "EXERCISE_ARCHIVED");

        assertThat(jdbc.queryForObject("select count(*) from workout_templates where owner_id is not null", Integer.class)).isZero();
    }

    // ---- replace -----------------------------------------------------------------------------

    @Test
    void replacesNameNotesAndTheWholeOrderedExerciseList() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID squat = builtInExercise("Barbell Back Squat");
        UUID row = builtInExercise("Barbell Row");
        UUID id = create(client, "Original", bench, squat);

        // Same exercises in a new order, plus a new one: the old rows must not collide with the new ones.
        JsonNode replaced = expect(client, client.put(TEMPLATES + "/" + id, Map.of("name", "Renamed", "notes", "New notes",
                "exercises", List.of(Map.of("exerciseId", squat.toString(), "targetSets", 4),
                        Map.of("exerciseId", row.toString()), Map.of("exerciseId", bench.toString())))), 200);

        assertThat(replaced.get("name").asText()).isEqualTo("Renamed");
        assertThat(replaced.get("notes").asText()).isEqualTo("New notes");
        assertThat(exerciseNames(replaced)).containsExactly("Barbell Back Squat", "Barbell Row", "Barbell Bench Press");
        assertThat(exerciseNames(expect(client, client.get(TEMPLATES + "/" + id), 200)))
                .containsExactly("Barbell Back Squat", "Barbell Row", "Barbell Bench Press");
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises where template_id = ?", Integer.class, id)).isEqualTo(3);
    }

    @Test
    void aFailedReplaceLeavesTheTemplateExactlyAsItWas() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID squat = builtInExercise("Barbell Back Squat");
        UUID id = create(client, "Keep Me", bench, squat);

        expectError(client, client.put(TEMPLATES + "/" + id, request("Changed", squat, UUID.randomUUID())), 404, "NOT_FOUND");
        expectError(client, client.put(TEMPLATES + "/" + id, request("Changed", bench, bench)), 400, "DUPLICATE_TEMPLATE_EXERCISE");
        expectError(client, client.put(TEMPLATES + "/" + id, Map.of("name", "Changed", "exercises", List.of())), 400, "VALIDATION_FAILED");

        JsonNode unchanged = expect(client, client.get(TEMPLATES + "/" + id), 200);
        assertThat(unchanged.get("name").asText()).isEqualTo("Keep Me");
        assertThat(exerciseNames(unchanged)).containsExactly("Barbell Bench Press", "Barbell Back Squat");
    }

    // ---- delete ------------------------------------------------------------------------------

    @Test
    void deletesOwnTemplateAndItsExerciseRows() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID id = create(client, "Temp", builtInExercise("Deadlift"));

        expect(client, client.delete(TEMPLATES + "/" + id), 204);

        expectError(client, client.get(TEMPLATES + "/" + id), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises where template_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void workoutsStartedFromADeletedTemplateKeepTheirData() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID id = create(client, "Temp", builtInExercise("Deadlift"));
        UUID workout = insertCompletedWorkout(user, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z"));
        jdbc.update("update workouts set template_id = ? where id = ?", id, workout);

        expect(client, client.delete(TEMPLATES + "/" + id), 204);

        assertThat(jdbc.queryForObject("select template_id from workouts where id = ?", UUID.class, workout)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from workouts where id = ?", Integer.class, workout)).isEqualTo(1);
    }

    // ---- duplicate ---------------------------------------------------------------------------

    @Test
    void duplicatesABuiltInTemplateIntoAnEditableCopy() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode copy = expect(client, client.post(TEMPLATES + "/" + builtInTemplate("Pull") + "/duplicate", Map.of()), 201);

        assertThat(copy.get("name").asText()).isEqualTo("Pull (copy)");
        assertThat(copy.get("builtIn").asBoolean()).isFalse();
        assertThat(exerciseNames(copy)).containsExactlyElementsOf(
                exerciseNames(expect(client, client.get(TEMPLATES + "/" + builtInTemplate("Pull")), 200)));
        assertThat(copy.get("exercises").get(0).get("targetSets").asInt()).isEqualTo(4);

        // The copy can be edited; the original is untouched.
        UUID copyId = UUID.fromString(copy.get("id").asText());
        expect(client, client.put(TEMPLATES + "/" + copyId, request("My Pull", builtInExercise("Pull-Up"))), 200);
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises where template_id = ?", Integer.class,
                builtInTemplate("Pull"))).isEqualTo(7);
    }

    @Test
    void duplicatingTwiceCreatesTwoIndependentCopies() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID push = builtInTemplate("Push");
        UUID first = UUID.fromString(expect(client, client.post(TEMPLATES + "/" + push + "/duplicate", Map.of()), 201).get("id").asText());
        UUID second = UUID.fromString(expect(client, client.post(TEMPLATES + "/" + push + "/duplicate", Map.of()), 201).get("id").asText());
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void duplicateTruncatesLongNamesAndSkipsArchivedExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID custom = insertExercise(user, "Soon Archived");
        UUID id = create(client, "N".repeat(100), bench, custom);
        jdbc.update("update exercises set archived_at = now() where id = ?", custom);

        JsonNode copy = expect(client, client.post(TEMPLATES + "/" + id + "/duplicate", Map.of()), 201);

        assertThat(copy.get("name").asText()).hasSize(100).endsWith(" (copy)");
        assertThat(exerciseNames(copy)).containsExactly("Barbell Bench Press");
        assertThat(copy.get("exercises").get(0).get("position").asInt()).isEqualTo(1);
    }

    // ---- ownership and access ----------------------------------------------------------------

    @Test
    void anotherUsersTemplateLooksLikeItDoesNotExist() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID secret = create(alice, "Alice Plan", builtInExercise("Deadlift"));

        expectError(bob, bob.get(TEMPLATES + "/" + secret), 404, "NOT_FOUND");
        expectError(bob, bob.put(TEMPLATES + "/" + secret, request("Mine", builtInExercise("Deadlift"))), 404, "NOT_FOUND");
        expectError(bob, bob.delete(TEMPLATES + "/" + secret), 404, "NOT_FOUND");
        expectError(bob, bob.post(TEMPLATES + "/" + secret + "/duplicate", Map.of()), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select name from workout_templates where id = ?", String.class, secret)).isEqualTo("Alice Plan");
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1", "size=x"})
    void rejectsInvalidPagingParameters(String query) throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.get(TEMPLATES + "?" + query), 400);
    }

    @Test
    void unknownAndMalformedIdsAreDistinguished() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.get(TEMPLATES + "/" + UUID.randomUUID()), 404, "NOT_FOUND");
        expect(client, client.get(TEMPLATES + "/not-a-uuid"), 400);
        expect(client, client.delete(TEMPLATES + "/not-a-uuid"), 400);
        expect(client, client.post(TEMPLATES + "/not-a-uuid/duplicate", Map.of()), 400);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        TestClient anonymous = new TestClient(mvc, mapper);
        UUID push = builtInTemplate("Push");
        expectError(anonymous, anonymous.get(TEMPLATES), 401, "UNAUTHENTICATED");
        expectError(anonymous, anonymous.get(TEMPLATES + "/" + push), 401, "UNAUTHENTICATED");
        expectError(anonymous, anonymous.post(TEMPLATES + "/" + push + "/duplicate", Map.of()), 401, "UNAUTHENTICATED");

        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        expectError(client, client.call(HttpMethod.POST, TEMPLATES, request("T", bench), false), 403, "CSRF_TOKEN_INVALID");
        expectError(client, client.call(HttpMethod.PUT, TEMPLATES + "/" + UUID.randomUUID(), request("T", bench), false), 403, "CSRF_TOKEN_INVALID");
        expectError(client, client.call(HttpMethod.DELETE, TEMPLATES + "/" + UUID.randomUUID(), null, false), 403, "CSRF_TOKEN_INVALID");
        expectError(client, client.call(HttpMethod.POST, TEMPLATES + "/" + push + "/duplicate", Map.of(), false), 403, "CSRF_TOKEN_INVALID");
    }
}
