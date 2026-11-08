package com.red_escuela.backend.graduation.usecase.request;

public interface VerifyProgramEnrollmentOwnerUseCase {
    void verify(Long programEnrollmentId, Long studentId);
}
