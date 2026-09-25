package com.personalos.backend.fitness;

import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Authentication, CSRF and id-format rules for every one of the 27 fitness endpoints. */
class FitnessAccessTest extends FitnessApiTest {

    /** {@code {u}} in the path stands for a UUID. */
    private record Endpoint(HttpMethod method, String path, Object body) {

        boolean mutating() { return method != HttpMethod.GET; }

        boolean hasId() { return path.contains("{u}"); }

        String with(String id) { return path.replace("{u}", id); }

        @Override public String toString() { return method + " " + path; }
    }

    private static final Object NEW_EXERCISE = Map.of("name", "Any", "primaryMuscleGroup", "BACK");
    private static final Object TEMPLATE_BODY = Map.of("name", "T",
            "exercises", List.of(Map.of("exerciseId", UUID.randomUUID().toString())));
    private static final Object SET_BODY = Map.of("weightKg", new BigDecimal("60"), "reps", 5);

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, "/api/v1/exercises", null),
            new Endpoint(HttpMethod.POST, "/api/v1/exercises", NEW_EXERCISE),
            new Endpoint(HttpMethod.PATCH, "/api/v1/exercises/{u}", Map.of("name", "X")),
            new Endpoint(HttpMethod.DELETE, "/api/v1/exercises/{u}", null),
            new Endpoint(HttpMethod.GET, "/api/v1/exercises/{u}/history", null),
            new Endpoint(HttpMethod.GET, "/api/v1/exercises/{u}/records", null),
            new Endpoint(HttpMethod.GET, "/api/v1/workout-templates", null),
            new Endpoint(HttpMethod.GET, "/api/v1/workout-templates/{u}", null),
            new Endpoint(HttpMethod.POST, "/api/v1/workout-templates", TEMPLATE_BODY),
            new Endpoint(HttpMethod.PUT, "/api/v1/workout-templates/{u}", TEMPLATE_BODY),
            new Endpoint(HttpMethod.DELETE, "/api/v1/workout-templates/{u}", null),
            new Endpoint(HttpMethod.POST, "/api/v1/workout-templates/{u}/duplicate", Map.of()),
            new Endpoint(HttpMethod.POST, "/api/v1/workouts", Map.of()),
            new Endpoint(HttpMethod.GET, "/api/v1/workouts/current", null),
            new Endpoint(HttpMethod.GET, "/api/v1/workouts", null),
            new Endpoint(HttpMethod.GET, "/api/v1/workouts/{u}", null),
            new Endpoint(HttpMethod.PATCH, "/api/v1/workouts/{u}", Map.of("name", "X")),
            new Endpoint(HttpMethod.POST, "/api/v1/workouts/{u}/exercises", Map.of("exerciseId", UUID.randomUUID().toString())),
            new Endpoint(HttpMethod.DELETE, "/api/v1/workouts/{u}/exercises/{u}", null),
            new Endpoint(HttpMethod.PUT, "/api/v1/workouts/{u}/exercises/order", Map.of("workoutExerciseIds", List.of())),
            new Endpoint(HttpMethod.PATCH, "/api/v1/workouts/{u}/exercises/{u}", Map.of("notes", "n")),
            new Endpoint(HttpMethod.POST, "/api/v1/workouts/{u}/exercises/{u}/sets", SET_BODY),
            new Endpoint(HttpMethod.PATCH, "/api/v1/workouts/{u}/exercises/{u}/sets/{u}", Map.of("reps", 5)),
            new Endpoint(HttpMethod.DELETE, "/api/v1/workouts/{u}/exercises/{u}/sets/{u}", null),
            new Endpoint(HttpMethod.POST, "/api/v1/workouts/{u}/finish", Map.of()),
            new Endpoint(HttpMethod.DELETE, "/api/v1/workouts/{u}", null),
            new Endpoint(HttpMethod.GET, "/api/v1/fitness/summary", null));

    private static String path(Endpoint e) {
        return e.with(UUID.randomUUID().toString());
    }

    @Test
    void thereAreTwentySevenEndpoints() {
        assertThat(ENDPOINTS).hasSize(27);
        assertThat(ENDPOINTS).allSatisfy(e -> assertThat(e.path()).startsWith("/api/v1/"));
    }

    @Test
    void everyEndpointRequiresAnAuthenticatedSession() throws Exception {
        TestClient anonymous = new TestClient(mvc, mapper);
        for (Endpoint e : ENDPOINTS) {
            // A valid CSRF token is sent, so the 401 comes from authentication and not from the CSRF check.
            expectError(anonymous, anonymous.call(e.method(), path(e), e.body(), e.mutating()), 401, "UNAUTHENTICATED");
        }
    }

    @Test
    void everyMutatingEndpointRequiresACsrfToken() throws Exception {
        TestClient client = newUser("a@example.com");
        long mutating = ENDPOINTS.stream().filter(Endpoint::mutating).count();
        assertThat(mutating).isEqualTo(18); // 27 endpoints, 9 of them reads
        for (Endpoint e : ENDPOINTS.stream().filter(Endpoint::mutating).toList()) {
            expectError(client, client.call(e.method(), path(e), e.body(), false), 403, "CSRF_TOKEN_INVALID");
        }
    }

    @Test
    void readEndpointsDoNotNeedACsrfToken() throws Exception {
        TestClient client = newUser("a@example.com");
        for (Endpoint e : ENDPOINTS.stream().filter(e -> !e.mutating() && !e.hasId()).toList()) {
            int status = client.call(e.method(), e.path(), null, false).getResponse().getStatus();
            assertThat(status).as(e.toString()).isIn(200, 204);
        }
    }

    @Test
    void malformedIdsAreRejectedAsBadRequests() throws Exception {
        TestClient client = newUser("a@example.com");
        for (Endpoint e : ENDPOINTS.stream().filter(Endpoint::hasId).toList()) {
            var result = client.call(e.method(), e.with("not-a-uuid"), e.body(), true);
            assertThat(result.getResponse().getStatus()).as(e.toString()).isEqualTo(400);
            assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                    .as(e.toString()).isEqualTo("BAD_REQUEST");
        }
    }

    @Test
    void wellFormedButUnknownIdsAreNotFound() throws Exception {
        TestClient client = newUser("a@example.com");
        for (Endpoint e : ENDPOINTS.stream().filter(Endpoint::hasId).toList()) {
            expectError(client, client.call(e.method(), path(e), e.body(), true), 404, "NOT_FOUND");
        }
    }

    @Test
    void anExpiredSessionIsRejectedLikeNoSession() throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.post("/api/v1/auth/logout", Map.of()), 204);
        client.fetchCsrf(); // logout rotates the CSRF token; get a valid one so the 401 comes from authentication
        for (Endpoint e : ENDPOINTS.stream().filter(e -> !e.hasId()).toList()) {
            expectError(client, client.call(e.method(), e.path(), e.body(), e.mutating()), 401, "UNAUTHENTICATED");
        }
    }
}
