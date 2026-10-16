package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.service.AllocationSourceQueryService;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditNoteShareSource;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditNoteSourceShare;
import com.red_escuela.backend.billing.note.exception.CreditNoteSourceIntegrityException;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AllocationSourceInvoiceCreditNoteShareSource implements InvoiceCreditNoteShareSource {

    private final AllocationSourceQueryService allocationSources;
    private final CreditNoteRepository creditNotes;

    public AllocationSourceInvoiceCreditNoteShareSource(AllocationSourceQueryService allocationSources, CreditNoteRepository creditNotes) {
        this.allocationSources = allocationSources;
        this.creditNotes = creditNotes;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<InvoiceCreditNoteSourceShare>> findCurrentCreditNoteSharesByInvoiceId(Collection<Long> invoiceIds) {
        Map<Long, List<PaymentAllocationSource>> sourcesByInvoice = allocationSources.findCurrentSourcesByInvoiceId(invoiceIds);
        Set<Long> creditNoteIds = sourcesByInvoice.values().stream().flatMap(List::stream)
                .map(PaymentAllocationSource::getCreditNoteId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (creditNoteIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> numbers = creditNotes.findCreditNoteNumbersByIds(creditNoteIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
        Map<Long, List<InvoiceCreditNoteSourceShare>> resolved = new LinkedHashMap<>();
        sourcesByInvoice.forEach((invoiceId, sources) -> {
            Map<Long, Money> appliedByCreditNote = new LinkedHashMap<>();
            for (PaymentAllocationSource source : sources) {
                Long creditNoteId = source.getCreditNoteId();
                if (creditNoteId == null) {
                    continue;
                }
                if (!numbers.containsKey(creditNoteId)) {
                    throw new CreditNoteSourceIntegrityException(creditNoteId);
                }
                appliedByCreditNote.merge(creditNoteId, source.getAmount().value(), Money::plus);
            }
            if (!appliedByCreditNote.isEmpty()) {
                resolved.put(invoiceId, appliedByCreditNote.entrySet().stream()
                        .map(entry -> new InvoiceCreditNoteSourceShare(entry.getKey(), numbers.get(entry.getKey()), entry.getValue())).toList());
            }
        });
        return resolved;
    }
}
