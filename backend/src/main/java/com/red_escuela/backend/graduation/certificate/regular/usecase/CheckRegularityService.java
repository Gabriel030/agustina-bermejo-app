package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CheckRegularityService implements CheckRegularityUseCase {

    private final RegularityChecker regularityChecker;

    public CheckRegularityService(RegularityChecker regularityChecker) {
        this.regularityChecker = regularityChecker;
    }

    @Override
    public RegularityResponse check(RegularityKind kind, Long enrollmentId) {
        return regularityChecker.check(kind, enrollmentId);
    }
}
