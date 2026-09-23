package com.personalos.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 72) String password
    ) {}

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword
    ) {}

    public record UserResponse(UUID id, String email, String displayName, boolean emailVerified) {}

    public record MessageResponse(String message) {}

    public record CsrfResponse(String headerName, String token) {}
}
