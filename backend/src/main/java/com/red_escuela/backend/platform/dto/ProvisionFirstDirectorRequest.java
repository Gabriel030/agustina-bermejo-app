package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.specialist.FeatureChangeReasonValidator;
import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProvisionFirstDirectorRequest(
        @NotBlank @Size(max = 255) String institutionName,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 50) String documentNumber,
        @NotBlank @Size(min = FeatureChangeReasonValidator.MIN_LENGTH, max = FeatureChangeReasonValidator.MAX_LENGTH) String reason) {
}
