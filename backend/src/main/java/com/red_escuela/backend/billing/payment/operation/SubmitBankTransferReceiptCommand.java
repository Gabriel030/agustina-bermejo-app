package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SubmitBankTransferReceiptCommand(Long studentId, List<Long> invoiceIds, Money declaredAmount, LocalDate transferDate, String operationNumber,
                                               String originAccount, String holderName, String bankName, String fileSha256,
                                               @JsonIgnore ReceiptFileStorage fileStorage, String idempotencyKey, StatusChangeActor actor)
        implements IdempotencyKeyed {

    public SubmitBankTransferReceiptCommand {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(invoiceIds, "invoiceIds");
        Objects.requireNonNull(actor, "actor");
        invoiceIds = List.copyOf(invoiceIds);
    }

    public boolean hasFile() {
        return fileStorage != null && fileSha256 != null && !fileSha256.isBlank();
    }
}
