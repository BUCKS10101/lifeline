package com.personalos.backend.wellness;

import com.personalos.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the database itself enforces the wellness rules, independent of the application code. */
@SpringBootTest
class WellnessSchemaTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    UUID userA;
    UUID userB;

    @BeforeEach
    void reset() {
        jdbc.execute("delete from users");
        userA = insertUser("a@example.com");
        userB = insertUser("b@example.com");
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, email_verified) values (?, ?, 'x', true)", id, email);
        return id;
    }

    private void sleep(UUID user, String date, String bed, String woke) {
        jdbc.update("insert into sleep_entries (id, user_id, sleep_date, bedtime_at, woke_at, zone_id) "
                + "values (?, ?, ?::date, ?::timestamptz, ?::timestamptz, 'UTC')", UUID.randomUUID(), user, date, bed, woke);
    }

    private void water(UUID user, String date, int ml) {
        jdbc.update("insert into water_entries (id, user_id, log_date, amount_ml, logged_at) values (?, ?, ?::date, ?, now())",
                UUID.randomUUID(), user, date, ml);
    }

    private void protein(UUID user, String date, int grams, String label) {
        jdbc.update("insert into protein_entries (id, user_id, log_date, grams, label, logged_at) values (?, ?, ?::date, ?, ?, now())",
                UUID.randomUUID(), user, date, grams, label);
    }

    private static void assertRejected(Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    // ---- sleep_entries -----------------------------------------------------------------------

    @Test
    void sleepDurationMustBeBetweenFifteenMinutesAndTwentyHours() {
        sleep(userA, "2026-09-01", "2026-08-31 23:45:00+00", "2026-09-01 00:00:00+00");          // exactly 15 minutes
        sleep(userA, "2026-09-02", "2026-09-01 12:00:00+00", "2026-09-02 08:00:00+00");          // exactly 20 hours
        assertRejected(() -> sleep(userA, "2026-09-03", "2026-09-02 23:46:00+00", "2026-09-03 00:00:00+00")); // 14 minutes
        assertRejected(() -> sleep(userA, "2026-09-04", "2026-09-03 11:59:00+00", "2026-09-04 08:00:00+00")); // 20 h 1 min
        assertThat(count("sleep_entries")).isEqualTo(2);
    }

    @Test
    void wakingMustBeAfterBedtime() {
        assertRejected(() -> sleep(userA, "2026-09-01", "2026-09-01 08:00:00+00", "2026-09-01 07:00:00+00"));
        assertRejected(() -> sleep(userA, "2026-09-01", "2026-09-01 08:00:00+00", "2026-09-01 08:00:00+00"));
    }

    @Test
    void sleepDatesBefore2000AreRejectedButThe1stOfJanuary2000IsAllowed() {
        assertRejected(() -> sleep(userA, "1999-12-31", "1999-12-30 23:00:00+00", "1999-12-31 07:00:00+00"));
        sleep(userA, "2000-01-01", "1999-12-31 23:00:00+00", "2000-01-01 07:00:00+00");
    }

    @Test
    void thereIsOneNightPerUserPerWakeDateButUsersAndDatesAreIndependent() {
        sleep(userA, "2026-09-01", "2026-08-31 23:00:00+00", "2026-09-01 07:00:00+00");
        assertRejected(() -> sleep(userA, "2026-09-01", "2026-08-31 22:00:00+00", "2026-09-01 06:00:00+00"));
        sleep(userB, "2026-09-01", "2026-08-31 23:00:00+00", "2026-09-01 07:00:00+00");
        sleep(userA, "2026-09-02", "2026-09-01 23:00:00+00", "2026-09-02 07:00:00+00");
        assertThat(count("sleep_entries")).isEqualTo(3);
    }

    @Test
    void sleepRequiresItsFieldsAndARealUser() {
        assertRejected(() -> jdbc.update("insert into sleep_entries (id, user_id, sleep_date, bedtime_at, woke_at, zone_id) "
                + "values (?, ?, '2026-09-01', null, '2026-09-01 07:00:00+00', 'UTC')", UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into sleep_entries (id, user_id, sleep_date, bedtime_at, woke_at, zone_id) "
                + "values (?, ?, '2026-09-01', '2026-08-31 23:00:00+00', '2026-09-01 07:00:00+00', null)", UUID.randomUUID(), userA));
        assertRejected(() -> sleep(UUID.randomUUID(), "2026-09-01", "2026-08-31 23:00:00+00", "2026-09-01 07:00:00+00"));
    }

    // ---- water_entries -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, 9, -250, 5001, 100000})
    void waterRejectsAmountsOutsideTenToFiveThousandMillilitres(int ml) {
        assertRejected(() -> water(userA, "2026-09-01", ml));
        assertThat(count("water_entries")).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 250, 500, 5000})
    void waterAcceptsAmountsInsideTheRange(int ml) {
        water(userA, "2026-09-01", ml);
        assertThat(count("water_entries")).isEqualTo(1);
    }

    @Test
    void waterAllowsManyEntriesPerDayAndRejectsDatesBefore2000() {
        for (int i = 0; i < 5; i++) water(userA, "2026-09-01", 250);
        assertThat(count("water_entries")).isEqualTo(5);
        assertRejected(() -> water(userA, "1999-12-31", 250));
    }

    // ---- protein_entries ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 501, 10000})
    void proteinRejectsGramsOutsideOneToFiveHundred(int grams) {
        assertRejected(() -> protein(userA, "2026-09-01", grams, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 25, 500})
    void proteinAcceptsGramsInsideTheRange(int grams) {
        protein(userA, "2026-09-01", grams, "Whey shake");
        assertThat(count("protein_entries")).isEqualTo(1);
    }

    @Test
    void proteinLabelsAreOptionalButNeverBlankOrLongerThanSixty() {
        protein(userA, "2026-09-01", 20, null);
        protein(userA, "2026-09-01", 20, "x".repeat(60));
        assertRejected(() -> protein(userA, "2026-09-01", 20, "   "));
        assertRejected(() -> protein(userA, "2026-09-01", 20, ""));
        assertRejected(() -> protein(userA, "2026-09-01", 20, "x".repeat(61)));
        assertRejected(() -> protein(userA, "1999-12-31", 20, null));
        assertThat(count("protein_entries")).isEqualTo(2);
    }

    // ---- wellness_preferences ----------------------------------------------------------------

    @Test
    void preferencesDefaultToEverythingShownAndNoGoals() {
        jdbc.update("insert into wellness_preferences (user_id) values (?)", userA);
        var row = jdbc.queryForMap("select * from wellness_preferences where user_id = ?", userA);
        assertThat(row.get("sleep_enabled")).isEqualTo(true);
        assertThat(row.get("water_enabled")).isEqualTo(true);
        assertThat(row.get("protein_enabled")).isEqualTo(true);
        assertThat(row.get("water_goal_ml")).isNull();
        assertThat(row.get("protein_goal_g")).isNull();
        assertThat(row.get("sleep_goal_minutes")).isNull();
    }

    @Test
    void goalsMustBeInsideTheirRanges() {
        String sql = "insert into wellness_preferences (user_id, %s) values (?, ?)";
        for (int[] bad : new int[][]{{249}, {10001}}) assertRejected(() -> jdbc.update(sql.formatted("water_goal_ml"), UUID.randomUUID(), bad[0]));
        assertRejected(() -> jdbc.update(sql.formatted("protein_goal_g"), userA, 9));
        assertRejected(() -> jdbc.update(sql.formatted("protein_goal_g"), userA, 501));
        assertRejected(() -> jdbc.update(sql.formatted("sleep_goal_minutes"), userA, 239));
        assertRejected(() -> jdbc.update(sql.formatted("sleep_goal_minutes"), userA, 961));
        jdbc.update("insert into wellness_preferences (user_id, water_goal_ml, protein_goal_g, sleep_goal_minutes) values (?, 250, 10, 240)", userA);
        jdbc.update("insert into wellness_preferences (user_id, water_goal_ml, protein_goal_g, sleep_goal_minutes) values (?, 10000, 500, 960)", userB);
        assertThat(count("wellness_preferences")).isEqualTo(2);
    }

    @Test
    void thereIsOnePreferencesRowPerUser() {
        jdbc.update("insert into wellness_preferences (user_id) values (?)", userA);
        assertRejected(() -> jdbc.update("insert into wellness_preferences (user_id) values (?)", userA));
    }

    // ---- ownership ---------------------------------------------------------------------------

    @Test
    void deletingAUserRemovesTheirWellnessDataOnly() {
        for (UUID user : new UUID[]{userA, userB}) {
            sleep(user, "2026-09-01", "2026-08-31 23:00:00+00", "2026-09-01 07:00:00+00");
            water(user, "2026-09-01", 250);
            protein(user, "2026-09-01", 25, "Whey");
            jdbc.update("insert into wellness_preferences (user_id) values (?)", user);
        }

        jdbc.update("delete from users where id = ?", userA);

        for (String table : new String[]{"sleep_entries", "water_entries", "protein_entries", "wellness_preferences"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userA)).as(table).isZero();
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userB)).as(table).isEqualTo(1);
        }
    }
}
