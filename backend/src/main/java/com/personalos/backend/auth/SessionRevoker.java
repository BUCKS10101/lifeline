package com.personalos.backend.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends every active session of a user, for example after a password reset. */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void revokeAll(String email) {
        sessions.findByPrincipalName(email).keySet().forEach(sessions::deleteById);
    }
}
