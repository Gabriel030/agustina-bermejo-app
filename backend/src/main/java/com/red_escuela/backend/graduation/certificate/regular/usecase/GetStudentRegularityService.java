package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentFactsFinder;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStudentRegularityService implements GetStudentRegularityUseCase {

    private final RegularEnrollmentFactsFinder factsFinder;
    private final CurrentStudentService currentStudentService;
    private final CheckRegularityUseCase checkRegularityUseCase;

    public GetStudentRegularityService(RegularEnrollmentFactsFinder factsFinder, CurrentStudentService currentStudentService,
                                       CheckRegularityUseCase checkRegularityUseCase) {
        this.factsFinder = factsFinder;
        this.currentStudentService = currentStudentService;
        this.checkRegularityUseCase = checkRegularityUseCase;
    }

    @Override
    public RegularityResponse regularityOf(RegularityKind kind, Long enrollmentId) {
        factsFinder.findOwnedBy(kind, enrollmentId, currentStudentService.resolveCurrentStudent().getId());
        return checkRegularityUseCase.check(kind, enrollmentId);
    }
}
