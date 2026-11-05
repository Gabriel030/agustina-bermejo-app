package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuardianRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 20) String documentNumber,
        @NotBlank @Size(max = 60) String relationship,
        @NotBlank @Size(max = 40) String phone,
        @Email @Size(max = 255) String email) {
}
