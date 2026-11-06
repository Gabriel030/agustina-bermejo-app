package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;

import java.util.List;

public interface ListRegularCertificateRequestsUseCase {

    List<RegularCertificateRequestResponse> list(Long studentId, RegularRequestOutcome outcome);
}
