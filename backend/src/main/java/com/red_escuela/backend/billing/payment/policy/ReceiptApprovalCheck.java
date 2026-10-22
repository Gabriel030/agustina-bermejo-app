package com.red_escuela.backend.billing.payment.policy;

import java.time.LocalDate;

public record ReceiptApprovalCheck(boolean legacyReceiptWithoutPayment, boolean verificationProvided, LocalDate uploadDate, LocalDate verifiedTransferDate) {
}
