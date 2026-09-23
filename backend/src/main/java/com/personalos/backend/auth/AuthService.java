package com.personalos.backend.auth;

import com.personalos.backend.auth.domain.EmailToken;
import com.personalos.backend.auth.domain.EmailTokenType;
import com.personalos.backend.auth.domain.User;
import com.personalos.backend.auth.domain.UserProfile;
import com.personalos.backend.auth.dto.AuthDtos.RegisterRequest;
import com.personalos.backend.auth.dto.AuthDtos.UserResponse;
import com.personalos.backend.auth.email.EmailSender;
import com.personalos.backend.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    static final Duration VERIFY_TTL = Duration.ofHours(24);
    static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final EmailTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailSender emailSender;
    private final SessionRevoker sessionRevoker;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String frontendUrl;

    public AuthService(UserRepository users, UserProfileRepository profiles, EmailTokenRepository tokens,
                       PasswordEncoder passwordEncoder, TokenService tokenService, EmailSender emailSender,
                       SessionRevoker sessionRevoker, TransactionTemplate tx, Clock clock,
                       @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.profiles = profiles;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.sessionRevoker = sessionRevoker;
        this.tx = tx;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Always completes silently, whether or not the email is already registered, so the
     * endpoint cannot be used to discover which emails have accounts.
     */
    public void register(RegisterRequest request) {
        String email = normalize(request.email());
        String hash = passwordEncoder.encode(request.password());

        String rawToken;
        try {
            rawToken = tx.execute(status -> {
                if (users.findByEmail(email).isPresent()) {
                    return null;
                }
                User user = users.saveAndFlush(new User(email, hash));
                profiles.save(new UserProfile(user.getId(), request.displayName().trim()));
                return issueToken(user.getId(), EmailTokenType.VERIFY_EMAIL, VERIFY_TTL);
            });
        } catch (DataIntegrityViolationException e) {
            rawToken = null; // lost a race with a concurrent registration of the same email
        }

        if (rawToken == null) {
            emailSender.send(email, "Personal OS: you already have an account",
                    "Someone tried to register with this email, but an account already exists.\n"
                            + "If it was you, log in or reset your password: " + frontendUrl + "/forgot-password");
        } else {
            emailSender.send(email, "Personal OS: verify your email",
                    "Welcome to Personal OS. Verify your email (link valid for 24 hours):\n"
                            + frontendUrl + "/verify-email?token=" + rawToken);
        }
    }

    public void verifyEmail(String rawToken) {
        tx.executeWithoutResult(status -> {
            EmailToken token = consume(rawToken, EmailTokenType.VERIFY_EMAIL);
            users.findById(token.getUserId()).orElseThrow(AuthService::invalidToken).markEmailVerified();
        });
    }

    public void forgotPassword(String rawEmail) {
        String email = normalize(rawEmail);
        String rawToken = tx.execute(status -> users.findByEmail(email)
                .map(user -> issueToken(user.getId(), EmailTokenType.RESET_PASSWORD, RESET_TTL))
                .orElse(null));
        if (rawToken != null) {
            emailSender.send(email, "Personal OS: reset your password",
                    "Reset your password (link valid for 1 hour):\n"
                            + frontendUrl + "/reset-password?token=" + rawToken);
        }
    }

    public void resetPassword(String rawToken, String newPassword) {
        String hash = passwordEncoder.encode(newPassword);
        String email = tx.execute(status -> {
            EmailToken token = consume(rawToken, EmailTokenType.RESET_PASSWORD);
            User user = users.findById(token.getUserId()).orElseThrow(AuthService::invalidToken);
            user.changePasswordHash(hash);
            user.markEmailVerified(); // receiving the reset link proves control of the mailbox
            return user.getEmail();
        });
        sessionRevoker.revokeAll(email);
    }

    public UserResponse currentUser(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Not authenticated"));
        String displayName = profiles.findById(userId).map(UserProfile::getDisplayName).orElse("");
        return new UserResponse(user.getId(), user.getEmail(), displayName, user.isEmailVerified());
    }

    private String issueToken(UUID userId, EmailTokenType type, Duration ttl) {
        Instant now = Instant.now(clock);
        tokens.invalidateUnused(userId, type, now);
        String raw = tokenService.generate();
        tokens.save(new EmailToken(userId, type, tokenService.hash(raw), now.plus(ttl)));
        return raw;
    }

    private EmailToken consume(String rawToken, EmailTokenType type) {
        Instant now = Instant.now(clock);
        EmailToken token = tokens.findByTokenHashAndType(tokenService.hash(rawToken), type)
                .filter(t -> t.isUsable(now))
                .orElseThrow(AuthService::invalidToken);
        token.markUsed(now);
        return token;
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "This link is invalid or has expired");
    }

    static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
