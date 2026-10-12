package com.red_escuela.backend.billing.configuration.policy;

import java.time.LocalDate;

public record ProposalApprovalCheck(LocalDate effectiveFrom, LocalDate today, LocalDate proposedOn) {
}
