package com.personalos.backend.auth;

import com.personalos.backend.auth.dto.AuthDtos.UpdateProfileRequest;
import com.personalos.backend.auth.dto.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal AppUserDetails principal,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(principal.getId(), request);
    }
}
