package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.time.LocalDate;
import java.util.List;

public class ReceiptTransferDateMismatchException extends BankTransferReceiptConflictException {

    public ReceiptTransferDateMismatchException(LocalDate uploadDate, LocalDate verifiedTransferDate) {
        super("The verified transfer date differs from the receipt upload date: the receipt was rejected",
                List.of(new PolicyViolation("RECEIPT_TRANSFER_DATE_MISMATCH", "La fecha de la transferencia (" + verifiedTransferDate
                        + ") no coincide con la fecha en que se subió el comprobante (" + uploadDate + "): el comprobante fue rechazado.")));
    }
}
