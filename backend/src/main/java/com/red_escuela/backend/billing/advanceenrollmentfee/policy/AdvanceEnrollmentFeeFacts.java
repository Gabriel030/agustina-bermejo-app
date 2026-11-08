package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import java.time.LocalDate;

public record AdvanceEnrollmentFeeFacts(LocalDate today, boolean nextYearConfigured, boolean enrollmentActive, boolean alreadyIssued, boolean overdueDebt,
                                        LocalDate opensOn, LocalDate closesOn) {
}
