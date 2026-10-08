package com.personalos.backend.auth.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests: a tiny local HTTP server (the JDK's own {@code HttpServer}, not a mocking library) stands in
 * for Resend's API, so no real network call is made and no API key is ever sent anywhere real.
 */
class ResendEmailSenderTest {

    private static final String FAKE_KEY = "re_test_super_secret_key_do_not_leak";
    private static final String FAKE_FROM = "no-reply@personal-os.local";

    private HttpServer server;
    private ListAppender<ILoggingEvent> logs;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int responseStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/emails", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(ResendEmailSender.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ((Logger) LoggerFactory.getLogger(ResendEmailSender.class)).detachAppender(logs);
    }

    private ResendEmailSender sender() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new ResendEmailSender(new ObjectMapper(), FAKE_KEY, baseUrl, FAKE_FROM);
    }

    private String everythingLogged() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            sb.append(event.getFormattedMessage()).append(' ');
            if (event.getThrowableProxy() != null) sb.append(event.getThrowableProxy().getMessage()).append(' ');
        }
        return sb.toString();
    }

    @Test
    void aSuccessfulResponseSendsTheRightRequest() throws Exception {
        responseStatus = 200;
        sender().send("person@example.com", "Verify your email", "Click https://x/verify?token=abc123");

        assertThat(lastAuth.get()).isEqualTo("Bearer " + FAKE_KEY);
        JsonNode body = new ObjectMapper().readTree(lastBody.get());
        assertThat(body.get("from").asText()).isEqualTo(FAKE_FROM);
        assertThat(body.get("to").get(0).asText()).isEqualTo("person@example.com");
        assertThat(body.get("to")).hasSize(1);
        assertThat(body.get("subject").asText()).isEqualTo("Verify your email");
        assertThat(body.get("text").asText()).isEqualTo("Click https://x/verify?token=abc123");
        assertThat(everythingLogged()).isEmpty(); // nothing is logged on success
    }

    @Test
    void theAuthorizationHeaderIsBearerPlusTheApiKey() throws Exception {
        sender().send("a@example.com", "Hello", "World");
        assertThat(lastAuth.get()).isEqualTo("Bearer " + FAKE_KEY);
    }

    @Test
    void aNonTwoXxResponseIsHandledAndLoggedWithoutLeakingTheApiKeyOrTheBody() {
        responseStatus = 422;
        sender().send("a@example.com", "Reset your password", "token=abc123shouldnotleak");

        String logged = everythingLogged();
        assertThat(logged).contains("422").contains("Reset your password");
        assertThat(logged).doesNotContain(FAKE_KEY);
        assertThat(logged).doesNotContain("abc123shouldnotleak");
    }

    @Test
    void theApiKeyIsNeverLoggedEvenWhenTheRequestFailsOutright() {
        server.stop(0); // force a connection failure: the exception path, not the non-2xx path
        sender().send("a@example.com", "Reset your password", "token=shouldnotleak");

        String logged = everythingLogged();
        assertThat(logged).doesNotContain(FAKE_KEY);
        assertThat(logged).doesNotContain("shouldnotleak");
        assertThat(logged).doesNotContain("Bearer"); // the Authorization header itself must never appear either
    }
}
