package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.Money;

record RefundRequestFingerprint(Long studentId, Money amount, String destinationAccount, String destinationHolder, Long requestedByPersonId) {
}
