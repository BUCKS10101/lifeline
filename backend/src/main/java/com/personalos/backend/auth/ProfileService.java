package com.personalos.backend.auth;

import com.personalos.backend.auth.domain.UserProfile;
import com.personalos.backend.auth.dto.AuthDtos.UpdateProfileRequest;
import com.personalos.backend.auth.dto.AuthDtos.UserResponse;
import com.personalos.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileService {

    private final UserProfileRepository profiles;
    private final AuthService authService;

    public ProfileService(UserProfileRepository profiles, AuthService authService) {
        this.profiles = profiles;
        this.authService = authService;
    }

    /** Updates the profile of the given user. The caller passes the authenticated user's id, never one from the request. */
    @Transactional
    public UserResponse update(UUID userId, UpdateProfileRequest request) {
        if (request.displayName() == null && request.timezone() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE",
                    "Provide displayName, timezone, or both");
        }

        UserProfile profile = profiles.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Profile not found"));
        if (request.displayName() != null) {
            profile.rename(request.displayName().trim());
        }
        if (request.timezone() != null) {
            profile.changeTimezone(request.timezone());
        }
        return authService.currentUser(userId);
    }
}
