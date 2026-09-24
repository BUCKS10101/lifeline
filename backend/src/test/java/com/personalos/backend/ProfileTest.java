package com.personalos.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.auth.dto.AuthDtos.LoginRequest;
import com.personalos.backend.auth.dto.AuthDtos.RegisterRequest;
import com.personalos.backend.auth.dto.AuthDtos.TokenRequest;
import com.personalos.backend.auth.dto.AuthDtos.UpdateProfileRequest;
import com.personalos.backend.support.AbstractIntegrationTest;
import com.personalos.backend.support.CapturingEmailSender;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailSender.class)
class ProfileTest extends AbstractIntegrationTest {

    private static final String PROFILE = "/api/v1/profile";
    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingEmailSender emails;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate table users cascade");
        emails.clear();
    }

    /** Registers, verifies and logs in a user; returns a client holding that user's session. */
    private TestClient loggedInUser(String email, String name, String timezone) throws Exception {
        TestClient client = new TestClient(mvc, mapper);
        client.post("/api/v1/auth/register", new RegisterRequest(email, PASSWORD, name, timezone));
        client.post("/api/v1/auth/verify-email", new TokenRequest(emails.lastToken()));
        assertThat(client.post("/api/v1/auth/login", new LoginRequest(email, PASSWORD)).getResponse().getStatus())
                .isEqualTo(200);
        return client;
    }

    private String timezoneInDb(String email) {
        return jdbc.queryForObject("select p.timezone from user_profiles p join users u on u.id = p.user_id "
                + "where u.email = ?", String.class, email);
    }

    private String nameInDb(String email) {
        return jdbc.queryForObject("select p.display_name from user_profiles p join users u on u.id = p.user_id "
                + "where u.email = ?", String.class, email);
    }

    @Test
    void registrationWithoutTimezoneDefaultsToUtc() throws Exception {
        TestClient client = loggedInUser("a@example.com", "A", null);
        assertThat(client.json(client.get("/api/v1/auth/me")).get("timezone").asText()).isEqualTo("UTC");
    }

    @Test
    void registrationStoresAValidTimezone() throws Exception {
        TestClient client = loggedInUser("a@example.com", "A", "Asia/Kolkata");
        assertThat(client.json(client.get("/api/v1/auth/me")).get("timezone").asText()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void registrationWithUnknownTimezoneFallsBackToUtcInsteadOfFailing() throws Exception {
        TestClient client = loggedInUser("a@example.com", "A", "Mars/Olympus_Mons");
        assertThat(timezoneInDb("a@example.com")).isEqualTo("UTC");
        assertThat(client.json(client.get("/api/v1/auth/me")).get("timezone").asText()).isEqualTo("UTC");
    }

    @Test
    void updatesDisplayNameAndTimezoneTogether() throws Exception {
        TestClient client = loggedInUser("a@example.com", "Old Name", null);

        var result = client.patch(PROFILE, new UpdateProfileRequest("  New Name ", "Asia/Kolkata"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = client.json(result);
        assertThat(body.get("displayName").asText()).isEqualTo("New Name");
        assertThat(body.get("timezone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(body.get("email").asText()).isEqualTo("a@example.com");
        // The change is persisted and visible on the next read.
        assertThat(client.json(client.get("/api/v1/auth/me")).get("timezone").asText()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void partialUpdateLeavesTheOtherFieldUntouched() throws Exception {
        TestClient client = loggedInUser("a@example.com", "Keep Me", "Europe/London");

        assertThat(client.patch(PROFILE, new UpdateProfileRequest(null, "America/New_York"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(nameInDb("a@example.com")).isEqualTo("Keep Me");
        assertThat(timezoneInDb("a@example.com")).isEqualTo("America/New_York");

        assertThat(client.patch(PROFILE, new UpdateProfileRequest("Renamed", null))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(nameInDb("a@example.com")).isEqualTo("Renamed");
        assertThat(timezoneInDb("a@example.com")).isEqualTo("America/New_York");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mars/Olympus_Mons", "IST", "+05:30", "asia/kolkata", "Asia/Kolkata ", "not a zone", ""})
    void invalidTimezoneIsRejectedAndNothingChanges(String timezone) throws Exception {
        TestClient client = loggedInUser("a@example.com", "Name", "Europe/London");

        var result = client.patch(PROFILE, new UpdateProfileRequest("Should Not Apply", timezone));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        var body = client.json(result);
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("violations").get(0).get("field").asText()).isEqualTo("timezone");
        assertThat(timezoneInDb("a@example.com")).isEqualTo("Europe/London");
        assertThat(nameInDb("a@example.com")).isEqualTo("Name");
    }

    @Test
    void blankDisplayNameIsRejected() throws Exception {
        TestClient client = loggedInUser("a@example.com", "Name", null);
        var result = client.patch(PROFILE, new UpdateProfileRequest("   ", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(client.json(result).get("violations").get(0).get("field").asText()).isEqualTo("displayName");
    }

    @Test
    void emptyUpdateIsRejected() throws Exception {
        TestClient client = loggedInUser("a@example.com", "Name", null);
        var result = client.patch(PROFILE, new UpdateProfileRequest(null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(client.json(result).get("code").asText()).isEqualTo("EMPTY_UPDATE");
    }

    @Test
    void aUserCanOnlyChangeTheirOwnProfile() throws Exception {
        TestClient alice = loggedInUser("alice@example.com", "Alice", "Europe/London");
        TestClient bob = loggedInUser("bob@example.com", "Bob", "Asia/Tokyo");

        // The endpoint has no user id parameter; extra fields naming someone else are ignored.
        Map<String, Object> sneaky = Map.of(
                "displayName", "Alice Updated",
                "timezone", "America/Chicago",
                "userId", "00000000-0000-0000-0000-000000000000",
                "email", "bob@example.com");
        assertThat(alice.patch(PROFILE, sneaky).getResponse().getStatus()).isEqualTo(200);

        assertThat(nameInDb("alice@example.com")).isEqualTo("Alice Updated");
        assertThat(timezoneInDb("alice@example.com")).isEqualTo("America/Chicago");
        assertThat(nameInDb("bob@example.com")).isEqualTo("Bob");
        assertThat(timezoneInDb("bob@example.com")).isEqualTo("Asia/Tokyo");
        assertThat(jdbc.queryForObject("select email from users where email = 'alice@example.com'", String.class))
                .isEqualTo("alice@example.com");

        var bobMe = bob.json(bob.get("/api/v1/auth/me"));
        assertThat(bobMe.get("displayName").asText()).isEqualTo("Bob");
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        TestClient anonymous = new TestClient(mvc, mapper);
        var result = anonymous.patch(PROFILE, new UpdateProfileRequest("X", "UTC"));
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(anonymous.json(result).get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void requestWithoutCsrfTokenIsRejectedEvenWhenLoggedIn() throws Exception {
        TestClient client = loggedInUser("a@example.com", "Name", "Europe/London");
        var result = client.patchWithoutCsrf(PROFILE, new UpdateProfileRequest("Hacked", "UTC"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(client.json(result).get("code").asText()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(nameInDb("a@example.com")).isEqualTo("Name");
        assertThat(timezoneInDb("a@example.com")).isEqualTo("Europe/London");
    }
}
