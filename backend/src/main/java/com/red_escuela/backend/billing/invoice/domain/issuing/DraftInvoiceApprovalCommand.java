package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Collection;

public record DraftInvoiceApprovalCommand(Long monthlyInvoiceRunId, Collection<Long> invoiceIds, LocalDate approvalDate, StatusChangeActor actor) {
}
