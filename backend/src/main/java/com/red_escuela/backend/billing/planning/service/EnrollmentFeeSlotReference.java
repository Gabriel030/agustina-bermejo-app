package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.YearCycle;

public record EnrollmentFeeSlotReference(YearCycle cycle, int slot) {
}
