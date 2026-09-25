package com.personalos.backend.weight;

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

/** Proves the database itself enforces the weight rules, independent of the application code. */
@SpringBootTest
class WeightSchemaTest extends AbstractIntegrationTest {

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

    private void insertEntry(UUID user, String date, String weight, String notes) {
        jdbc.update("insert into weight_entries (id, user_id, entry_date, weight_kg, notes) "
                + "values (?, ?, ?::date, ?::numeric, ?)", UUID.randomUUID(), user, date, weight, notes);
    }

    private void insertTarget(UUID user, String weight) {
        jdbc.update("insert into weight_targets (user_id, target_weight_kg, started_on) values (?, ?::numeric, current_date)", user, weight);
    }

    private static void assertRejected(Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    // ---- weight_entries ----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"19.99", "0", "-1", "-62.5", "500.01", "1000", "999.99"})
    void entryRejectsWeightsOutsideTwentyToFiveHundredKilograms(String weight) {
        assertRejected(() -> insertEntry(userA, "2026-09-01", weight, null));
        assertThat(count("weight_entries")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "20.01", "62.5", "82.35", "499.99", "500"})
    void entryAcceptsWeightsInsideTheRange(String weight) {
        insertEntry(userA, "2026-09-01", weight, null);
        assertThat(count("weight_entries")).isEqualTo(1);
    }

    @Test
    void entryDatesBefore2000AreRejectedButThe1stOfJanuary2000IsAllowed() {
        assertRejected(() -> insertEntry(userA, "1999-12-31", "70", null));
        assertRejected(() -> insertEntry(userA, "1900-01-01", "70", null));
        insertEntry(userA, "2000-01-01", "70", null);
    }

    @Test
    void thereIsOneEntryPerUserPerDayButUsersAndDaysAreIndependent() {
        insertEntry(userA, "2026-09-01", "70", null);
        assertRejected(() -> insertEntry(userA, "2026-09-01", "71", null));   // same user, same day
        insertEntry(userB, "2026-09-01", "80", null);                        // another user, same day
        insertEntry(userA, "2026-09-02", "70.5", null);                      // same user, another day
        assertThat(count("weight_entries")).isEqualTo(3);
    }

    @Test
    void notesAreLimitedTo500Characters() {
        insertEntry(userA, "2026-09-01", "70", "n".repeat(500));
        assertRejected(() -> insertEntry(userA, "2026-09-02", "70", "n".repeat(501)));
    }

    @Test
    void weightAndDateAreRequired() {
        assertRejected(() -> jdbc.update("insert into weight_entries (id, user_id, entry_date, weight_kg) values (?, ?, '2026-09-01', null)",
                UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into weight_entries (id, user_id, entry_date, weight_kg) values (?, ?, null, 70)",
                UUID.randomUUID(), userA));
    }

    @Test
    void anEntryMustBelongToARealUser() {
        assertRejected(() -> insertEntry(UUID.randomUUID(), "2026-09-01", "70", null));
    }

    @Test
    void deletingAUserRemovesTheirEntriesAndTargetOnly() {
        insertEntry(userA, "2026-09-01", "70", null);
        insertEntry(userA, "2026-09-02", "71", null);
        insertEntry(userB, "2026-09-01", "80", null);
        insertTarget(userA, "65");
        insertTarget(userB, "75");

        jdbc.update("delete from users where id = ?", userA);

        assertThat(jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, userA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from weight_targets where user_id = ?", Integer.class, userA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, userB)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from weight_targets where user_id = ?", Integer.class, userB)).isEqualTo(1);
    }

    // ---- weight_targets ----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"19.99", "0", "-70", "500.01", "5000"})
    void targetRejectsWeightsOutsideTwentyToFiveHundredKilograms(String weight) {
        assertRejected(() -> insertTarget(userA, weight));
        assertThat(count("weight_targets")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "65", "72.5", "500"})
    void targetAcceptsWeightsInsideTheRange(String weight) {
        insertTarget(userA, weight);
    }

    @Test
    void aUserHasAtMostOneTarget() {
        insertTarget(userA, "65");
        assertRejected(() -> insertTarget(userA, "60"));
        insertTarget(userB, "60");
        assertThat(count("weight_targets")).isEqualTo(2);
    }

    @Test
    void aTargetNeedsAStartDateAndARealUser() {
        assertRejected(() -> jdbc.update("insert into weight_targets (user_id, target_weight_kg, started_on) values (?, 65, null)", userA));
        assertRejected(() -> jdbc.update("insert into weight_targets (user_id, target_weight_kg, started_on) values (?, 65, current_date)", UUID.randomUUID()));
    }
}
