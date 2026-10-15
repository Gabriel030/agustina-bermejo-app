package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import com.red_escuela.backend.billing.note.domain.CreditNote;

public record LateFeeCorrectionIssued(CreditNote creditNote, LateFeeChargeCorrection correction) {
}
