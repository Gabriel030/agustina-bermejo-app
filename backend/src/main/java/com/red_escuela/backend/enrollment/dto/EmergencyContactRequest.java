package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 60) String relationship,
        @NotBlank @Size(max = 40) String phone) {
}
