package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.InvoiceTargetedRequest;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public record CancelInvoicesAfterWithdrawalCommand(List<WithdrawalCancellationItem> items, LocalDate withdrawalDate, Long requestedByPersonId,
                                                   boolean requestedByDirector, Long authorizationRequestId)
        implements DirectorApprovable, InvoiceTargetedRequest, ResumedFromAuthorizationRequest<CancelInvoicesAfterWithdrawalCommand> {
    private static final DateTimeFormatter DAY_AND_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    public CancelInvoicesAfterWithdrawalCommand {
        Objects.requireNonNull(withdrawalDate, "withdrawalDate");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("A withdrawal cancellation needs at least one invoice");
        }
    }

    public String justification() {
        return "Baja del " + withdrawalDate.format(DAY_AND_MONTH) + ": período posterior a la baja";
    }

    @Override
    public List<Long> targetedInvoiceIds() {
        return items.stream().map(WithdrawalCancellationItem::invoiceId).toList();
    }

    @Override
    public String authorizationReason() {
        return "Baja del " + withdrawalDate.format(DAY_AND_MONTH) + ": cancelación de " + items.size() + " factura(s) de períodos posteriores a la baja";
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return null;
    }

    @Override
    public CancelInvoicesAfterWithdrawalCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new CancelInvoicesAfterWithdrawalCommand(items, withdrawalDate, requestedByPersonId, requestedByDirector, authorizationRequestId);
    }
}
