package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record BankTransferReceiptDetailsCheck(Money declaredAmount, LocalDate transferDate, LocalDate today, String operationNumber, String originAccount,
                                              String holderName, String bankName, boolean hasFile, int selectedInvoiceCount) {
}
