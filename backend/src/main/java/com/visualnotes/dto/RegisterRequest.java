package com.visualnotes.dto;

import java.util.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 15, max = 128) String password) {
    public RegisterRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[password=REDACTED]";
    }
}
