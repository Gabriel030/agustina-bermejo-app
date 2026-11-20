package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.StudentRegularCertificateStateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface GetStudentRegularCertificateStateUseCase {

    StudentRegularCertificateStateResponse stateOf(RegularityKind kind, Long enrollmentId);
}
