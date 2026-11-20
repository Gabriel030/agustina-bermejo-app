package com.red_escuela.backend.graduation.certificate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IssueGraduationCertificateBatchRequest(@NotNull @Size(min = 1, max = 50) List<@Valid IssueGraduationCertificateRequest> items) {
}
