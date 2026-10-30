package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.service.ProgramEnrollmentOwnershipChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyProgramEnrollmentOwnerService implements VerifyProgramEnrollmentOwnerUseCase {

    private final ProgramEnrollmentOwnershipChecker programEnrollmentOwnershipChecker;

    public VerifyProgramEnrollmentOwnerService(ProgramEnrollmentOwnershipChecker programEnrollmentOwnershipChecker) {
        this.programEnrollmentOwnershipChecker = programEnrollmentOwnershipChecker;
    }

    @Override
    public void verify(Long programEnrollmentId, Long studentId) {
        programEnrollmentOwnershipChecker.requireOwnedBy(programEnrollmentId, studentId);
    }
}
