package com.red_escuela.backend.graduation.certificate.exam.dto;

import java.util.List;

public record ExamCertificateBatchResponse(int issuedCount, int skippedCount, List<ExamCertificateBatchItemResponse> items) {
}
