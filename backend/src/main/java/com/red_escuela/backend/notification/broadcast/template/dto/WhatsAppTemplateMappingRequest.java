package com.red_escuela.backend.notification.broadcast.template.dto;

import com.red_escuela.backend.notification.broadcast.template.domain.WhatsAppTemplateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WhatsAppTemplateMappingRequest(
        @NotNull Long messageTemplateId,
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9_]+$") String providerTemplateName,
        @NotNull @Pattern(regexp = "^[a-z]{2}(_[A-Z]{2})?$") String languageCode,
        @NotNull WhatsAppTemplateStatus status,
        @NotNull @Size(max = 10) List<@NotBlank String> variableOrder) {
}
