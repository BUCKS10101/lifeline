package com.personalos.backend.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(UUID userId, String displayName, String timezone) {
        this.userId = userId;
        this.displayName = displayName;
        this.timezone = timezone;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getTimezone() { return timezone; }

    public void rename(String displayName) { this.displayName = displayName; }
    public void changeTimezone(String timezone) { this.timezone = timezone; }
}
