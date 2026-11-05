package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface RegularityRule {

    RegularityKind kind();

    RegularityVerdict evaluate(Long enrollmentId);
}
