package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.CreditNoteSettlement;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CreditNoteSettlementQueryService {
    private final CreditNoteRepository creditNoteRepository;

    public CreditNoteSettlementQueryService(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
    }

    @Transactional(readOnly = true)
    public Optional<CreditNoteSettlement> findDebtReductionCreditNoteThatSettledInvoice(Long invoiceId) {
        return creditNoteRepository.findByInvoiceIdNewestFirst(invoiceId).stream()
                .filter(note -> note.getReason() != CreditNoteReason.CANCELLATION && !note.getOffsetAmount().value().isZero())
                .findFirst()
                .map(note -> new CreditNoteSettlement(note.getId(), note.getIssueDate()));
    }
}
