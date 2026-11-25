package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;

@Component
public class ApprovedSubjectsOwnEnrollmentResolver {

    private final CurrentActorFinder currentActorFinder;
    private final OwnProgramEnrollmentResolver enrollmentResolver;

    public ApprovedSubjectsOwnEnrollmentResolver(CurrentActorFinder currentActorFinder, OwnProgramEnrollmentResolver enrollmentResolver) {
        this.currentActorFinder = currentActorFinder;
        this.enrollmentResolver = enrollmentResolver;
    }

    public Long resolve(GenerateOwnDocumentRequest request) {
        Long requestedEnrollmentId = request == null ? null : request.enrollmentId();
        return enrollmentResolver.resolve(currentActorFinder.currentStudentId(), requestedEnrollmentId)
                .enrollmentId()
                .orElseThrow(ProcedureNotEligibleException::new);
    }
}
