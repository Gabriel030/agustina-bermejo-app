package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
import com.red_escuela.backend.billing.collections.domain.LateFeeSettlement;
import com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource;
import com.red_escuela.backend.billing.note.service.CreditNoteSettlementQueryService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LateFeeSettlementResolver {
    private final InvoiceSettlementQueryService allocationQuery;
    private final CreditNoteSettlementQueryService creditNoteQuery;

    public LateFeeSettlementResolver(InvoiceSettlementQueryService allocationQuery, CreditNoteSettlementQueryService creditNoteQuery) {
        this.allocationQuery = allocationQuery;
        this.creditNoteQuery = creditNoteQuery;
    }

    public Optional<LateFeeSettlement> findSettlementOfInvoice(Long invoiceId) {
        Optional<LateFeeSettlement> byAllocation = allocationQuery.findCurrentSettlementOfInvoice(invoiceId)
                .map(settlement -> new LateFeeSettlement(settlement.paymentDate(), LateFeeSettlementSource.ofAllocation(settlement.allocationId())));
        if (byAllocation.isPresent()) {
            return byAllocation;
        }
        return creditNoteQuery.findDebtReductionCreditNoteThatSettledInvoice(invoiceId)
                .map(settlement -> new LateFeeSettlement(settlement.issueDate(), LateFeeSettlementSource.ofCreditNote(settlement.creditNoteId())));
    }
}
