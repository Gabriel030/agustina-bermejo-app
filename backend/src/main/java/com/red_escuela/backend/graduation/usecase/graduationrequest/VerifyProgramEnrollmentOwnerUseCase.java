package com.red_escuela.backend.graduation.usecase.graduationrequest;

public interface VerifyProgramEnrollmentOwnerUseCase {
    void verify(Long programEnrollmentId, Long studentId);
}
