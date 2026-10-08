package com.personalos.backend.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Resend's HTTP API over HTTPS, instead of SMTP. Some hosts (e.g. Render's free tier) block
 * outbound SMTP ports entirely, so a plain HTTPS POST is what actually gets through in production. Used whenever
 * {@code app.mail.provider} is "resend"; local development keeps using {@link SmtpEmailSender} against Mailhog.
 *
 * <p>Uses the JDK's own {@link HttpClient}, not a Resend SDK: this is a single POST with a bearer token, which
 * does not warrant a new dependency.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String apiKey;
    private final String from;

    public ResendEmailSender(ObjectMapper mapper,
                             @Value("${app.mail.resend.api-key:}") String apiKey,
                             @Value("${app.mail.resend.base-url:https://api.resend.com}") String baseUrl,
                             @Value("${app.mail.from}") String from) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = mapper;
        this.endpoint = URI.create(baseUrl + "/emails");
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "text", body));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Never the response body or the Authorization header: either could echo back request content.
                log.error("Resend rejected an email with subject '{}': HTTP {}", subject, response.statusCode());
            }
        } catch (Exception e) {
            // A mail outage must not turn into an error that reveals whether the account exists, and the body
            // (which can carry a verification or reset token) and the API key must never reach the log.
            log.error("Failed to send email with subject '{}'", subject, e);
        }
    }
}
