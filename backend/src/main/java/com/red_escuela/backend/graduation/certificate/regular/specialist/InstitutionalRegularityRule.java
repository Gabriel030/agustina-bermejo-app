package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface InstitutionalRegularityRule {

    RegularityKind kind();

    RegularityVerdict evaluate(Long enrollmentId);
}
