package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.specialist.ProcedureEligibilityStrategies;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class GetProcedureEligibilityService implements GetProcedureEligibilityUseCase {

    private static final String UNAVAILABLE_SUMMARY = "Este trámite todavía no está disponible";

    private final ProcedureEligibilityStrategies strategies;
    private final CurrentActorFinder currentActorFinder;

    public GetProcedureEligibilityService(ProcedureEligibilityStrategies strategies, CurrentActorFinder currentActorFinder) {
        this.strategies = strategies;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ProcedureEligibilityResponse eligibilityOf(StudentProcedureType type, Long enrollmentId, Long registrationId) {
        Long studentId = currentActorFinder.currentStudentId();
        return strategies.forType(type)
                .map(strategy -> strategy.eligibilityOf(studentId, enrollmentId, registrationId))
                .orElseGet(() -> new ProcedureEligibilityResponse(type, false, enrollmentId, UNAVAILABLE_SUMMARY, List.of()));
    }
}
