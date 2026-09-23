package com.personalos.backend.support;

import com.personalos.backend.auth.email.EmailSender;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records outgoing emails so tests can read the verification and reset links. */
@TestComponent
@Primary
public class CapturingEmailSender implements EmailSender {

    public record Email(String to, String subject, String body) {}

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<Email> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Email(to, subject, body));
    }

    public void clear() { sent.clear(); }
    public List<Email> all() { return List.copyOf(sent); }

    public Email last() {
        return sent.get(sent.size() - 1);
    }

    public String lastToken() {
        Matcher m = TOKEN.matcher(last().body());
        if (!m.find()) throw new IllegalStateException("No token in: " + last().body());
        return m.group(1);
    }
}
