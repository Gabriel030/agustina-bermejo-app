package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

@Component
public class DraftInvoiceBatchDiscarder {
    private static final Logger log = LoggerFactory.getLogger(DraftInvoiceBatchDiscarder.class);

    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DraftInvoiceBatchDiscarder(InvoiceRepository invoiceRepository, ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> discardDraftsOfRun(Long monthlyInvoiceRunId, Collection<Long> invoiceIds, String reason, StatusChangeActor actor) {
        List<Invoice> drafts = DraftInvoiceLocking.lockPendingDraftsOfRun(invoiceRepository, monthlyInvoiceRunId, new TreeSet<>(invoiceIds));
        drafts.forEach(draft -> {
            draft.discardDraftInvoice(reason, actor);
            draft.pullRecordedDomainEvents().stream().filter(InvoiceDiscardedEvent.class::isInstance).forEach(eventPublisher::publishEvent);
        });
        log.info("Borradores descartados: corrida={}, cantidad={}, motivo={}", monthlyInvoiceRunId, drafts.size(), reason);
        return drafts;
    }
}
