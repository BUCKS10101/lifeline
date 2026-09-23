package com.personalos.backend.auth;

import com.personalos.backend.auth.domain.EmailToken;
import com.personalos.backend.auth.domain.EmailTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndType(String tokenHash, EmailTokenType type);

    /** Invalidates earlier unused tokens so only the most recent link works. */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    void invalidateUnused(UUID userId, EmailTokenType type, Instant now);
}
