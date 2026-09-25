package com.personalos.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.auth.dto.AuthDtos.LoginRequest;
import com.personalos.backend.auth.dto.AuthDtos.RegisterRequest;
import com.personalos.backend.auth.dto.AuthDtos.TokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared setup for API integration tests in any module: a real PostgreSQL and Redis, registered and logged-in
 * users, a controllable clock, and JSON assertion helpers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CapturingEmailSender.class, MutableClock.class})
public abstract class ApiTestBase extends AbstractIntegrationTest {

    protected static final String PASSWORD = "correct horse battery";
    protected static final Instant START = Instant.parse("2026-09-24T10:00:00Z");

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected CapturingEmailSender emails;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void resetState() {
        // Foreign-key cascades remove every module's per-user data; built-in exercises and templates have no owner and stay.
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
}
