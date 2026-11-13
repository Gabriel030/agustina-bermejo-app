package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface GetStudentRegularityUseCase {

    RegularityResponse regularityOf(RegularityKind kind, Long enrollmentId);
}
