package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;

public final class WithdrawalCancellationPlanner {
    private static final Set<InvoicePaymentStatus> UNPAID_AND_NOT_UNDER_REVIEW = Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE);

    private final YearMonth withdrawalMonth;
    private final LocalDate registeredOn;
    private final LongFunction<Optional<LocalDate>> termStartOfTemplate;

    public WithdrawalCancellationPlanner(YearMonth withdrawalMonth, LocalDate registeredOn, LongFunction<Optional<LocalDate>> termStartOfTemplate) {
        this.withdrawalMonth = withdrawalMonth;
        this.registeredOn = registeredOn;
        this.termStartOfTemplate = termStartOfTemplate;
    }

    public WithdrawalCancellationPlan planFor(Collection<Invoice> enrollmentInvoices, Set<Long> invoiceIdsWithPendingRequest) {
        List<WithdrawalCancellationItem> requestItems = new ArrayList<>();
        List<Long> automatic = new ArrayList<>();
        List<Long> drafts = new ArrayList<>();
        for (Invoice invoice : enrollmentInvoices) {
            if (invoice.getDocumentStatus() == InvoiceDocumentStatus.DRAFT) {
                if (!invoice.getLines().isEmpty() && invoice.getLines().stream().allMatch(this::startsAfterWithdrawal)) {
                    drafts.add(invoice.getId());
                }
                continue;
            }
            boolean cancellable = invoice.getDocumentStatus() == InvoiceDocumentStatus.ISSUED && UNPAID_AND_NOT_UNDER_REVIEW.contains(invoice.getPaymentStatus())
                    && !invoiceIdsWithPendingRequest.contains(invoice.getId());
            if (cancellable) {
                planIssuedInvoice(invoice, requestItems, automatic);
            }
        }
        return new WithdrawalCancellationPlan(requestItems, automatic, drafts);
    }

    private void planIssuedInvoice(Invoice invoice, List<WithdrawalCancellationItem> requestItems, List<Long> automatic) {
        List<InvoiceLine> chargedLines = invoice.getLines().stream().filter(line -> !line.calculateCancellableAmount().value().isZero()).toList();
        List<InvoiceLine> laterLines = chargedLines.stream().filter(this::startsAfterWithdrawal).toList();
        if (laterLines.isEmpty()) {
            return;
        }
        if (laterLines.size() < chargedLines.size()) {
            requestItems.add(WithdrawalCancellationItem.reducing(invoice.getId(),
                    laterLines.stream().map(line -> new WithdrawalLineAmount(line.getLineNumber(), line.calculateCreditableAmount().value())).toList()));
        } else if (laterLines.stream().allMatch(line -> line.getFeeType() == FeeType.ENROLLMENT_FEE)) {
            automatic.add(invoice.getId());
        } else {
            requestItems.add(WithdrawalCancellationItem.cancelling(invoice.getId()));
        }
    }

    private boolean startsAfterWithdrawal(InvoiceLine line) {
        return switch (line.getFeeType()) {
            case MONTHLY_INSTALLMENT -> line.getPeriod().isAfter(withdrawalMonth);
            case ENROLLMENT_FEE -> line.getTemplateId() != null && termStartOfTemplate.apply(line.getTemplateId()).map(registeredOn::isBefore).orElse(false);
            default -> false;
        };
    }
}
