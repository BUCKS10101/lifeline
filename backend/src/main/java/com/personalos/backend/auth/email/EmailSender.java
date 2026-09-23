package com.personalos.backend.auth.email;

/** Port for outgoing email. SMTP now; a Resend implementation can replace it without touching callers. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
