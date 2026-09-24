package com.personalos.backend;

import com.personalos.backend.auth.dto.AuthDtos.*;
import com.personalos.backend.support.AbstractIntegrationTest;
import com.personalos.backend.support.CapturingEmailSender;
import com.personalos.backend.support.TestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailSender.class)
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String EMAIL = "gov@example.com";
    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingEmailSender emails;

    TestClient client;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate table users cascade");
        emails.clear();
        client = new TestClient(mvc, mapper);
    }

    private void register() throws Exception {
        assertThat(client.post("/api/v1/auth/register",
                new RegisterRequest(EMAIL, PASSWORD, "Gov", null)).getResponse().getStatus()).isEqualTo(202);
    }

    private void registerAndVerify() throws Exception {
        register();
        assertThat(client.post("/api/v1/auth/verify-email",
                new TokenRequest(emails.lastToken())).getResponse().getStatus()).isEqualTo(200);
    }

    private int login(String email, String password) throws Exception {
        return client.post("/api/v1/auth/login", new LoginRequest(email, password)).getResponse().getStatus();
    }

    @Test
    void registerVerifyLoginMeLogout() throws Exception {
        register();
        assertThat(emails.last().to()).isEqualTo(EMAIL);

        // Not verified yet: correct password is still refused, with a specific code.
        var blocked = client.post("/api/v1/auth/login", new LoginRequest(EMAIL, PASSWORD));
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(client.json(blocked).get("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");

        String token = emails.lastToken();
        assertThat(client.post("/api/v1/auth/verify-email", new TokenRequest(token)).getResponse().getStatus())
                .isEqualTo(200);
        // Single use.
        var reused = client.post("/api/v1/auth/verify-email", new TokenRequest(token));
        assertThat(reused.getResponse().getStatus()).isEqualTo(400);
        assertThat(client.json(reused).get("code").asText()).isEqualTo("INVALID_TOKEN");

        var loggedIn = client.post("/api/v1/auth/login", new LoginRequest(EMAIL.toUpperCase(), PASSWORD));
        assertThat(loggedIn.getResponse().getStatus()).isEqualTo(200);
        assertThat(client.json(loggedIn).get("email").asText()).isEqualTo(EMAIL);
        assertThat(client.json(loggedIn).has("passwordHash")).isFalse();

        var me = client.get("/api/v1/auth/me");
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(client.json(me).get("displayName").asText()).isEqualTo("Gov");

        assertThat(client.post("/api/v1/auth/logout", java.util.Map.of()).getResponse().getStatus()).isEqualTo(204);
        assertThat(client.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void secretsAreNeverStoredInPlainText() throws Exception {
        register();
        String rawToken = emails.lastToken();
        String storedHash = jdbc.queryForObject("select password_hash from users", String.class);
        String storedToken = jdbc.queryForObject("select token_hash from email_tokens", String.class);
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(storedToken).isNotEqualTo(rawToken).hasSize(64);
    }

    @Test
    void duplicateRegistrationLooksIdenticalAndCreatesNoSecondAccount() throws Exception {
        register();
        emails.clear();
        var again = client.post("/api/v1/auth/register", new RegisterRequest(EMAIL, PASSWORD, "Other", null));
        assertThat(again.getResponse().getStatus()).isEqualTo(202);
        assertThat(emails.last().subject()).contains("already have an account");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        registerAndVerify();
        var wrong = client.post("/api/v1/auth/login", new LoginRequest(EMAIL, "not the password"));
        var unknown = client.post("/api/v1/auth/login", new LoginRequest("nobody@example.com", PASSWORD));
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(client.json(wrong).get("message")).isEqualTo(client.json(unknown).get("message"));
        assertThat(client.json(wrong).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void mutatingRequestWithoutCsrfTokenIsRejected() throws Exception {
        var result = client.postWithoutCsrf("/api/v1/auth/register", new RegisterRequest(EMAIL, PASSWORD, "Gov", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(client.json(result).get("code").asText()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }

    @Test
    void protectedEndpointReturnsJsonErrorWhenLoggedOut() throws Exception {
        var result = client.get("/api/v1/auth/me");
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(client.json(result).get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void validationErrorsListTheOffendingFields() throws Exception {
        var result = client.post("/api/v1/auth/register", new RegisterRequest("not-an-email", "short", "", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        var body = client.json(result);
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("violations").size()).isEqualTo(3);
    }

    @Test
    void unknownRouteUsesTheStandardErrorFormat() throws Exception {
        registerAndVerify();
        login(EMAIL, PASSWORD);
        var result = client.get("/api/v1/does-not-exist");
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(client.json(result).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        register();
        jdbc.update("update email_tokens set expires_at = now() - interval '1 minute'");
        var result = client.post("/api/v1/auth/verify-email", new TokenRequest(emails.lastToken()));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void passwordResetChangesPasswordAndRevokesSessions() throws Exception {
        registerAndVerify();
        assertThat(login(EMAIL, PASSWORD)).isEqualTo(200);
        assertThat(client.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(200);

        // A second browser stays logged in until the reset happens.
        TestClient other = new TestClient(mvc, mapper);
        assertThat(other.post("/api/v1/auth/login", new LoginRequest(EMAIL, PASSWORD)).getResponse().getStatus())
                .isEqualTo(200);

        emails.clear();
        assertThat(client.post("/api/v1/auth/forgot-password", new ForgotPasswordRequest(EMAIL))
                .getResponse().getStatus()).isEqualTo(202);
        String resetToken = emails.lastToken();

        String newPassword = "a brand new passphrase";
        assertThat(client.post("/api/v1/auth/reset-password", new ResetPasswordRequest(resetToken, newPassword))
                .getResponse().getStatus()).isEqualTo(200);

        assertThat(client.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
        assertThat(other.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);

        TestClient fresh = new TestClient(mvc, mapper);
        assertThat(fresh.post("/api/v1/auth/login", new LoginRequest(EMAIL, PASSWORD)).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(fresh.post("/api/v1/auth/login", new LoginRequest(EMAIL, newPassword)).getResponse().getStatus())
                .isEqualTo(200);

        // The reset link is single use.
        assertThat(fresh.post("/api/v1/auth/reset-password", new ResetPasswordRequest(resetToken, "yet another one!"))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void forgotPasswordForUnknownEmailLooksTheSameAndSendsNothing() throws Exception {
        var result = client.post("/api/v1/auth/forgot-password", new ForgotPasswordRequest("ghost@example.com"));
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(emails.all()).isEmpty();
    }
}
