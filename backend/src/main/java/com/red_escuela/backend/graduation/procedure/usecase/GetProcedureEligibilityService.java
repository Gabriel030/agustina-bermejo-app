package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.examregistration.specialist.ExamRegistrationEligibilityEvaluator;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProcedureEligibilityService implements GetProcedureEligibilityUseCase {

    private final ExamRegistrationEligibilityEvaluator examRegistrationEvaluator;

    public GetProcedureEligibilityService(ExamRegistrationEligibilityEvaluator examRegistrationEvaluator) {
        this.examRegistrationEvaluator = examRegistrationEvaluator;
    }

    @Override
    public ProcedureEligibilityResponse eligibilityOf(StudentProcedureType type, Long enrollmentId, Long registrationId) {
        if (type == StudentProcedureType.EXAM_REGISTRATION_CERTIFICATE) {
            return examRegistrationEvaluator.evaluate(registrationId);
        }
        throw new DocumentOperationPendingException();
    }
}
