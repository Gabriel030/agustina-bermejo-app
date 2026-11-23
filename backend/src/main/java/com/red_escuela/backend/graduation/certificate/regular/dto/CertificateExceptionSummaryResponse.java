package com.red_escuela.backend.graduation.certificate.regular.dto;

public record CertificateExceptionSummaryResponse(long pending, long approved, long rejected, long withdrawn, long expired, long consumed) {
}
