package com.personalos.backend.fitness;

import com.personalos.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the database itself enforces the fitness rules, independent of the application code. */
@SpringBootTest
class FitnessSchemaTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    UUID userA;
    UUID userB;

    @BeforeEach
    void reset() {
        jdbc.execute("delete from users");
        userA = insertUser("a@example.com");
        userB = insertUser("b@example.com");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, email_verified) values (?, ?, 'x', true)", id, email);
        return id;
    }

    private UUID insertExercise(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exercises (id, owner_id, name, primary_muscle_group) values (?, ?, ?, 'CHEST')",
                id, owner, name);
        return id;
    }

    private UUID insertWorkout(UUID user, String status) {
        UUID id = UUID.randomUUID();
        if (status.equals("COMPLETED")) {
            jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at) "
                    + "values (?, ?, 'W', 'COMPLETED', current_date, now(), now())", id, user);
        } else {
            jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at) "
                    + "values (?, ?, 'W', 'IN_PROGRESS', current_date, now())", id, user);
        }
        return id;
    }

    private UUID insertWorkoutExercise(UUID workout, UUID exercise, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workout_exercises (id, workout_id, exercise_id, position) values (?, ?, ?, ?)",
                id, workout, exercise, position);
        return id;
    }

    private void insertSet(UUID workoutExercise, int number, String weight, int reps, String rpe) {
        jdbc.update("insert into workout_sets (id, workout_exercise_id, set_number, weight_kg, reps, rpe) "
                        + "values (?, ?, ?, ?::numeric, ?, ?::numeric)",
                UUID.randomUUID(), workoutExercise, number, weight, reps, rpe);
    }

    private static void assertRejected(Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID workoutExerciseFixture() {
        return insertWorkoutExercise(insertWorkout(userA, "IN_PROGRESS"), insertExercise(userA, "Lift"), 1);
    }

    // ---- migrations and seed data ------------------------------------------------------------

    @Test
    void seedContainsBuiltInExercisesAcrossMuscleGroups() {
        Integer builtIns = jdbc.queryForObject("select count(*) from exercises where owner_id is null", Integer.class);
        Integer groups = jdbc.queryForObject(
                "select count(distinct primary_muscle_group) from exercises where owner_id is null", Integer.class);
        assertThat(builtIns).isGreaterThanOrEqualTo(40);
        assertThat(groups).isGreaterThanOrEqualTo(9);
        assertThat(jdbc.queryForObject("select count(*) from exercises where name = 'Barbell Bench Press' "
                + "and owner_id is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void seedContainsPushPullAndLegsTemplatesWithOrderedExercises() {
        List<String> names = jdbc.queryForList(
                "select name from workout_templates where owner_id is null order by name", String.class);
        assertThat(names).containsExactly("Legs", "Pull", "Push");

        for (String template : names) {
            List<Integer> positions = jdbc.queryForList("select x.position from workout_template_exercises x "
                    + "join workout_templates t on t.id = x.template_id where t.name = ? and t.owner_id is null "
                    + "order by x.position", Integer.class, template);
            assertThat(positions).as(template).isNotEmpty();
            assertThat(positions).as(template + " positions are 1..n")
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, positions.size()).boxed().toList());
        }
        // Every seeded template exercise resolves to a built-in exercise.
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises x "
                + "join exercises e on e.id = x.exercise_id where e.owner_id is not null", Integer.class)).isZero();
    }

    // ---- exercises ---------------------------------------------------------------------------

    @Test
    void builtInNamesAreUniqueIgnoringCase() {
        assertRejected(() -> insertExercise(null, "barbell BENCH press"));
    }

    @Test
    void namesAreUniquePerOwnerIgnoringCaseButNotAcrossOwners() {
        insertExercise(userA, "Cable Row");
        assertRejected(() -> insertExercise(userA, "cable row"));
        insertExercise(userB, "Cable Row"); // another user may use the same name
    }

    @Test
    void anArchivedExerciseFreesItsName() {
        UUID id = insertExercise(userA, "Old Name");
        jdbc.update("update exercises set archived_at = now() where id = ?", id);
        insertExercise(userA, "Old Name");
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id = ?", Integer.class, userA)).isEqualTo(2);
    }

    @Test
    void exerciseRejectsBlankNamesAndUnknownMuscleGroups() {
        assertRejected(() -> insertExercise(userA, "   "));
        assertRejected(() -> jdbc.update("insert into exercises (id, owner_id, name, primary_muscle_group) "
                + "values (?, ?, 'X', 'WINGS')", UUID.randomUUID(), userA));
    }

    // ---- workouts ----------------------------------------------------------------------------

    @Test
    void aUserCanHaveOnlyOneInProgressWorkout() {
        insertWorkout(userA, "IN_PROGRESS");
        assertRejected(() -> insertWorkout(userA, "IN_PROGRESS"));
        insertWorkout(userB, "IN_PROGRESS");              // other users are independent
        insertWorkout(userA, "COMPLETED");                // completed workouts are unlimited
        insertWorkout(userA, "COMPLETED");
    }

    @Test
    void workoutStatusMustMatchFinishedAt() {
        assertRejected(() -> jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at) "
                + "values (?, ?, 'W', 'COMPLETED', current_date, now())", UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at) "
                + "values (?, ?, 'W', 'IN_PROGRESS', current_date, now(), now())", UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at) "
                + "values (?, ?, 'W', 'COMPLETED', current_date, now(), now() - interval '1 hour')", UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at) "
                + "values (?, ?, 'W', 'ABANDONED', current_date, now())", UUID.randomUUID(), userA));
    }

    @Test
    void anExerciseAppearsOnlyOncePerWorkoutAndPositionsAreUnique() {
        UUID workout = insertWorkout(userA, "IN_PROGRESS");
        UUID bench = insertExercise(userA, "Bench");
        UUID squat = insertExercise(userA, "Squat");
        insertWorkoutExercise(workout, bench, 1);
        assertRejected(() -> insertWorkoutExercise(workout, bench, 2));
        assertRejected(() -> insertWorkoutExercise(workout, squat, 1)); // deferred unique, checked at commit
        insertWorkoutExercise(workout, squat, 2);
    }

    @Test
    void positionsCanBeSwappedInsideOneTransaction() {
        UUID workout = insertWorkout(userA, "IN_PROGRESS");
        UUID a = insertWorkoutExercise(workout, insertExercise(userA, "A"), 1);
        UUID b = insertWorkoutExercise(workout, insertExercise(userA, "B"), 2);

        tx.executeWithoutResult(s -> {
            jdbc.update("update workout_exercises set position = 2 where id = ?", a);
            jdbc.update("update workout_exercises set position = 1 where id = ?", b);
        });

        assertThat(jdbc.queryForObject("select position from workout_exercises where id = ?", Integer.class, a)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select position from workout_exercises where id = ?", Integer.class, b)).isEqualTo(1);
    }

    // ---- sets --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-5", "1000.01", "5000"})
    void setRejectsWeightsOutsideZeroToOneThousand(String weight) {
        UUID we = workoutExerciseFixture();
        assertRejected(() -> insertSet(we, 1, weight, 5, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.5", "62.5", "1000"})
    void setAcceptsWeightsIncludingZeroForBodyweight(String weight) {
        UUID we = workoutExerciseFixture();
        insertSet(we, 1, weight, 5, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000})
    void setRejectsRepsOutsideOneToOneHundred(int reps) {
        UUID we = workoutExerciseFixture();
        assertRejected(() -> insertSet(we, 1, "50", reps, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.5", "10.5", "11", "7.3", "7.25", "-1"})
    void setRejectsInvalidRpe(String rpe) {
        UUID we = workoutExerciseFixture();
        assertRejected(() -> insertSet(we, 1, "50", 5, rpe));
    }

    @Test
    void setAcceptsRpeInHalfStepsOrNone() {
        UUID we = workoutExerciseFixture();
        insertSet(we, 1, "50", 5, null);
        insertSet(we, 2, "50", 5, "1");
        insertSet(we, 3, "50", 5, "7.5");
        insertSet(we, 4, "50", 5, "10");
    }

    @Test
    void setNumbersAreUniquePerExerciseAndPositive() {
        UUID we = workoutExerciseFixture();
        insertSet(we, 1, "50", 5, null);
        assertRejected(() -> insertSet(we, 1, "60", 5, null));
        assertRejected(() -> insertSet(we, 0, "60", 5, null));
    }

    // ---- foreign keys and cascades -----------------------------------------------------------

    @Test
    void anExerciseUsedInAWorkoutCannotBeDeleted() {
        UUID exercise = insertExercise(userA, "Used");
        insertWorkoutExercise(insertWorkout(userA, "COMPLETED"), exercise, 1);
        assertRejected(() -> jdbc.update("delete from exercises where id = ?", exercise));
    }

    @Test
    void deletingAWorkoutRemovesItsExercisesAndSets() {
        UUID workout = insertWorkout(userA, "IN_PROGRESS");
        UUID we = insertWorkoutExercise(workout, insertExercise(userA, "Lift"), 1);
        insertSet(we, 1, "50", 5, null);

        jdbc.update("delete from workouts where id = ?", workout);

        assertThat(jdbc.queryForObject("select count(*) from workout_exercises where id = ?", Integer.class, we)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isZero();
    }

    @Test
    void deletingAUserRemovesEverythingTheyOwnEvenWhenTheirExercisesAreInTheirWorkouts() {
        UUID we = workoutExerciseFixture();
        insertSet(we, 1, "50", 5, null);
        UUID template = UUID.randomUUID();
        jdbc.update("insert into workout_templates (id, owner_id, name) values (?, ?, 'T')", template, userA);

        jdbc.update("delete from users where id = ?", userA);

        assertThat(jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, userA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id = ?", Integer.class, userA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workout_templates where owner_id = ?", Integer.class, userA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isZero();
        // Built-ins are untouched.
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id is null", Integer.class)).isPositive();
    }

    @Test
    void deletingATemplateKeepsWorkoutsStartedFromIt() {
        UUID template = UUID.randomUUID();
        jdbc.update("insert into workout_templates (id, owner_id, name) values (?, ?, 'T')", template, userA);
        UUID workout = UUID.randomUUID();
        jdbc.update("insert into workouts (id, user_id, template_id, name, status, performed_on, started_at, finished_at) "
                + "values (?, ?, ?, 'W', 'COMPLETED', current_date, now(), now())", workout, userA, template);

        jdbc.update("delete from workout_templates where id = ?", template);

        assertThat(jdbc.queryForObject("select template_id from workouts where id = ?", UUID.class, workout)).isNull();
    }

    @Test
    void templateExercisesAreUniquePerTemplateAndOrdered() {
        UUID template = UUID.randomUUID();
        jdbc.update("insert into workout_templates (id, owner_id, name) values (?, ?, 'T')", template, userA);
        UUID bench = insertExercise(userA, "Bench");
        UUID squat = insertExercise(userA, "Squat");
        jdbc.update("insert into workout_template_exercises (id, template_id, exercise_id, position, target_sets) "
                + "values (?, ?, ?, 1, 3)", UUID.randomUUID(), template, bench);
        assertRejected(() -> jdbc.update("insert into workout_template_exercises (id, template_id, exercise_id, position) "
                + "values (?, ?, ?, 2)", UUID.randomUUID(), template, bench));
        assertRejected(() -> jdbc.update("insert into workout_template_exercises (id, template_id, exercise_id, position) "
                + "values (?, ?, ?, 1)", UUID.randomUUID(), template, squat));
        assertRejected(() -> jdbc.update("insert into workout_template_exercises (id, template_id, exercise_id, position, target_sets) "
                + "values (?, ?, ?, 2, 21)", UUID.randomUUID(), template, squat));
    }
}
