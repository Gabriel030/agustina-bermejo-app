package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.specialist.RegularProcedureEligibility;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProcedureEligibilityService implements GetProcedureEligibilityUseCase {

    private final RegularProcedureEligibility regularEligibility;
    private final CurrentStudentService currentStudentService;

    public GetProcedureEligibilityService(RegularProcedureEligibility regularEligibility,
                                          CurrentStudentService currentStudentService) {
        this.regularEligibility = regularEligibility;
        this.currentStudentService = currentStudentService;
    }

    @Override
    public ProcedureEligibilityResponse eligibilityOf(StudentProcedureType type, Long enrollmentId, Long registrationId) {
        if (type == StudentProcedureType.REGULAR_STUDENT_CERTIFICATE) {
            return regularEligibility.evaluate(currentStudentService.resolveCurrentStudent().getId(), enrollmentId);
        }
        throw new DocumentOperationPendingException();
    }
}
