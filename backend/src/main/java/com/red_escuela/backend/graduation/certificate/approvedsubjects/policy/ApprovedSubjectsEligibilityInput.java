package com.red_escuela.backend.graduation.certificate.approvedsubjects.policy;

import com.red_escuela.backend.shared.module.BillingMode;

public record ApprovedSubjectsEligibilityInput(
        BillingMode billingMode,
        boolean hasOverdueDebt,
        boolean institutionallyRegular,
        long issuedToday,
        int dailyIssuanceLimit,
        int approvedSubjectsCount,
        boolean hasActiveEnrollment) {
}
