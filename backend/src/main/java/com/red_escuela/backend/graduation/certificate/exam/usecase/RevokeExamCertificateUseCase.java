package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;

public interface RevokeExamCertificateUseCase {

    ExamCertificateResponse revoke(Long id, String reason);
}
