package com.personalos.backend.auth;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Authenticated principal stored in the session. Serializable because sessions live in Redis. */
public class AppUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final boolean emailVerified;
    private String passwordHash;

    public AppUserDetails(UUID id, String email, String passwordHash, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
    }

    public UUID getId() { return id; }
    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public void eraseCredentials() { this.passwordHash = null; }
}
