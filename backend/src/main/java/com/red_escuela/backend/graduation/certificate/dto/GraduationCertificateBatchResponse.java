package com.red_escuela.backend.graduation.certificate.dto;

import java.util.List;

public record GraduationCertificateBatchResponse(
        int requested,
        int issued,
        int failed,
        List<GraduationCertificateBatchItemResponse> results) {
}
