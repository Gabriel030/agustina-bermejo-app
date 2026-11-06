package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.service.FeatureChangeReasonValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeInstituteSettingRequest(
        @NotNull Boolean enabled,
        @NotBlank @Size(min = FeatureChangeReasonValidator.MIN_LENGTH, max = FeatureChangeReasonValidator.MAX_LENGTH) String reason) {
}
