package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface CheckRegularityUseCase {

    RegularityResponse check(RegularityKind kind, Long enrollmentId);
}
