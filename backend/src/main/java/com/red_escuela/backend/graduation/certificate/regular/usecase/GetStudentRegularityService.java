package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentFactsFinder;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityChecker;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStudentRegularityService implements GetStudentRegularityUseCase {

    private final RegularEnrollmentFactsFinder factsFinder;
    private final CurrentStudentService currentStudentService;
    private final RegularityChecker regularityChecker;

    public GetStudentRegularityService(RegularEnrollmentFactsFinder factsFinder, CurrentStudentService currentStudentService,
                                       RegularityChecker regularityChecker) {
        this.factsFinder = factsFinder;
        this.currentStudentService = currentStudentService;
        this.regularityChecker = regularityChecker;
    }

    @Override
    public RegularityResponse regularityOf(RegularityKind kind, Long enrollmentId) {
        factsFinder.findOwnedBy(kind, enrollmentId, currentStudentService.resolveCurrentStudent().getId());
        return regularityChecker.check(kind, enrollmentId);
    }
}
