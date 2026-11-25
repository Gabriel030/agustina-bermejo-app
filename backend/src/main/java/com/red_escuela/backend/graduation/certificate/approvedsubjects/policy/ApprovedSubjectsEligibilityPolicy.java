package com.red_escuela.backend.graduation.certificate.approvedsubjects.policy;

import com.red_escuela.backend.shared.module.BillingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ApprovedSubjectsEligibilityPolicy {

    public List<ApprovedSubjectsBlocker> blockersOf(ApprovedSubjectsEligibilityInput input) {
        Objects.requireNonNull(input, "input");
        if (!input.hasActiveEnrollment()) {
            return List.of(ApprovedSubjectsBlocker.NO_ACTIVE_ENROLLMENT);
        }
        List<ApprovedSubjectsBlocker> blockers = new ArrayList<>();
        if (!input.institutionallyRegular()) {
            blockers.add(ApprovedSubjectsBlocker.NOT_REGULAR_STUDENT);
        }
        if (blocksForPayments(input)) {
            blockers.add(ApprovedSubjectsBlocker.PENDING_PAYMENTS);
        }
        if (input.issuedToday() >= input.dailyIssuanceLimit()) {
            blockers.add(ApprovedSubjectsBlocker.ISSUANCE_LIMIT_REACHED);
        }
        if (input.approvedSubjectsCount() <= 0) {
            blockers.add(ApprovedSubjectsBlocker.NO_APPROVED_SUBJECTS);
        }
        return List.copyOf(blockers);
    }

    private boolean blocksForPayments(ApprovedSubjectsEligibilityInput input) {
        BillingMode mode = Objects.requireNonNull(input.billingMode(), "billingMode");
        return switch (mode) {
            case OFF -> false;
            case MIRROR -> true;
            case FULL -> input.hasOverdueDebt();
        };
    }
}
