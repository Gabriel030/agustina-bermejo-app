package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public interface OverdueDebtProbe {

    boolean hasOverdueDebt(Long studentId);

    boolean isEnrollmentFeePaid(RegularityKind kind, Long enrollmentId);
}
