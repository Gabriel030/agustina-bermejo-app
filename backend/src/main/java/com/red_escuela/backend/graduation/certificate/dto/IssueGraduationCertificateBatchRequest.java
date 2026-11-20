package com.red_escuela.backend.graduation.certificate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IssueGraduationCertificateBatchRequest(@NotNull List<@Valid IssueGraduationCertificateRequest> items) {
}
