package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface OverdueDebtProbe {

    boolean hasOverdueDebt(Long studentId);

    boolean isEnrollmentFeePaid(RegularityKind kind, Long enrollmentId);
}
