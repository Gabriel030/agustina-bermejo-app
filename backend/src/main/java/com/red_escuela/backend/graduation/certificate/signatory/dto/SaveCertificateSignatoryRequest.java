package com.red_escuela.backend.graduation.certificate.signatory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveCertificateSignatoryRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 80) String role,
        @NotNull List<SignatoryDocumentType> allowedDocumentTypes) {
}
