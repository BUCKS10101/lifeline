package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.auth.dto.AuthDtos.LoginRequest;
import com.personalos.backend.auth.dto.AuthDtos.RegisterRequest;
import com.personalos.backend.auth.dto.AuthDtos.TokenRequest;
import com.personalos.backend.support.AbstractIntegrationTest;
import com.personalos.backend.support.CapturingEmailSender;
import com.personalos.backend.support.MutableClock;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared setup for fitness API tests: users, a controllable clock, and SQL fixtures for history data. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CapturingEmailSender.class, MutableClock.class})
public abstract class FitnessApiTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "correct horse battery";
    protected static final Instant START = Instant.parse("2026-09-24T10:00:00Z");

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected CapturingEmailSender emails;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void resetFitnessState() {
        // Cascades remove users' data; built-in exercises and templates have no owner and stay.
        jdbc.execute("delete from users");
        emails.clear();
        clock.set(START);
    }

    // ---- users -------------------------------------------------------------------------------

    protected TestClient newUser(String email) throws Exception {
        return newUser(email, "UTC");
    }

    /** Registers, verifies and logs in a user; the returned client holds that user's session. */
    protected TestClient newUser(String email, String timezone) throws Exception {
        TestClient client = new TestClient(mvc, mapper);
        client.post("/api/v1/auth/register", new RegisterRequest(email, PASSWORD, email.split("@")[0], timezone));
        client.post("/api/v1/auth/verify-email", new TokenRequest(emails.lastToken()));
        expect(client, client.post("/api/v1/auth/login", new LoginRequest(email, PASSWORD)), 200);
        return client;
    }

    protected UUID userId(TestClient client) throws Exception {
        return UUID.fromString(expect(client, client.get("/api/v1/auth/me"), 200).get("id").asText());
    }

    // ---- assertions --------------------------------------------------------------------------

    /** Asserts the status and returns the JSON body (an empty node when there is none). */
    protected JsonNode expect(TestClient client, MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as("status of %s %s: %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                        result.getResponse().getContentAsString())
                .isEqualTo(status);
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? mapper.createObjectNode() : mapper.readTree(content);
    }

    protected void expectError(TestClient client, MvcResult result, int status, String code) throws Exception {
        JsonNode body = expect(client, result, status);
        assertThat(body.path("code").asText()).as("error code").isEqualTo(code);
    }

    // ---- ids of seeded data ------------------------------------------------------------------

    protected UUID builtInExercise(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name = ?", UUID.class, name);
    }

    protected UUID builtInTemplate(String name) {
        return jdbc.queryForObject("select id from workout_templates where owner_id is null and name = ?", UUID.class, name);
    }

    // ---- SQL fixtures (history data without going through the workout API) -------------------

    protected UUID insertExercise(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exercises (id, owner_id, name, primary_muscle_group) values (?, ?, ?, 'CHEST')",
                id, owner, name);
        return id;
    }

    /** A logged set: {@code s("60", 8)} is a working set, {@code w("40", 10)} a warm-up. */
    protected record SetSpec(String weight, int reps, boolean warmup) {}

    protected static SetSpec s(String weight, int reps) { return new SetSpec(weight, reps, false); }

    protected static SetSpec w(String weight, int reps) { return new SetSpec(weight, reps, true); }

    protected UUID insertCompletedWorkout(UUID user, LocalDate performedOn, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at) "
                        + "values (?, ?, 'Fixture', 'COMPLETED', ?, ?, ?)",
                id, user, performedOn, java.sql.Timestamp.from(startedAt), java.sql.Timestamp.from(startedAt.plusSeconds(3600)));
        return id;
    }

    protected UUID insertInProgressWorkout(UUID user, LocalDate performedOn, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at) "
                        + "values (?, ?, 'Fixture', 'IN_PROGRESS', ?, ?)",
                id, user, performedOn, java.sql.Timestamp.from(startedAt));
        return id;
    }

    protected UUID addExerciseWithSets(UUID workout, UUID exercise, int position, SetSpec... sets) {
        UUID we = UUID.randomUUID();
        jdbc.update("insert into workout_exercises (id, workout_id, exercise_id, position) values (?, ?, ?, ?)",
                we, workout, exercise, position);
        int number = 1;
        for (SetSpec set : sets) {
            jdbc.update("insert into workout_sets (id, workout_exercise_id, set_number, weight_kg, reps, is_warmup) "
                            + "values (?, ?, ?, ?::numeric, ?, ?)",
                    UUID.randomUUID(), we, number++, set.weight(), set.reps(), set.warmup());
        }
        return we;
    }

    // ---- workout API helpers -----------------------------------------------------------------

    protected static final String WORKOUTS = "/api/v1/workouts";

    protected JsonNode startWorkout(TestClient client) throws Exception {
        return expect(client, client.post(WORKOUTS, Map.of()), 201);
    }

    protected JsonNode startFromTemplate(TestClient client, UUID templateId) throws Exception {
        return expect(client, client.post(WORKOUTS, Map.of("templateId", templateId.toString())), 201);
    }

    protected JsonNode getWorkout(TestClient client, String workoutId) throws Exception {
        return expect(client, client.get(WORKOUTS + "/" + workoutId), 200);
    }

    protected JsonNode finishWorkout(TestClient client, String workoutId) throws Exception {
        return expect(client, client.post(WORKOUTS + "/" + workoutId + "/finish", Map.of()), 200);
    }

    /** Adds an exercise to a workout and returns the new workout-exercise entry. */
    protected JsonNode addExercise(TestClient client, String workoutId, UUID exerciseId) throws Exception {
        return expect(client, client.post(WORKOUTS + "/" + workoutId + "/exercises",
                Map.of("exerciseId", exerciseId.toString())), 201);
    }

    protected JsonNode logSet(TestClient client, String workoutId, String workoutExerciseId, String weight, int reps)
            throws Exception {
        return expect(client, client.post(setsPath(workoutId, workoutExerciseId),
                Map.of("weightKg", new BigDecimal(weight), "reps", reps)), 201);
    }

    protected JsonNode logWarmup(TestClient client, String workoutId, String workoutExerciseId, String weight, int reps)
            throws Exception {
        return expect(client, client.post(setsPath(workoutId, workoutExerciseId),
                Map.of("weightKg", new BigDecimal(weight), "reps", reps, "warmup", true)), 201);
    }

    protected static String setsPath(String workoutId, String workoutExerciseId) {
        return WORKOUTS + "/" + workoutId + "/exercises/" + workoutExerciseId + "/sets";
    }

    /** Starts an empty workout, logs the given working sets of one exercise, finishes it, and returns the detail. */
    protected JsonNode completedWorkout(TestClient client, UUID exercise, Object... weightRepsPairs) throws Exception {
        String workoutId = startWorkout(client).get("id").asText();
        String weId = addExercise(client, workoutId, exercise).get("id").asText();
        for (int i = 0; i < weightRepsPairs.length; i += 2) {
            logSet(client, workoutId, weId, weightRepsPairs[i].toString(), (Integer) weightRepsPairs[i + 1]);
        }
        return finishWorkout(client, workoutId);
    }

    protected List<String> names(JsonNode workoutDetail) {
        return workoutDetail.get("exercises").findValues("exercise").stream().map(e -> e.get("name").asText()).toList();
    }
}
