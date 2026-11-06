package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.run.dto.DraftInvoiceBatchResponse;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftReviewer;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Collection;

@UseCase
public class DiscardDraftInvoicesService implements DiscardDraftInvoicesUseCase {
    private final MonthlyRunDraftReviewer draftReviewer;
    private final BillingActorResolver actorResolver;

    public DiscardDraftInvoicesService(MonthlyRunDraftReviewer draftReviewer, BillingActorResolver actorResolver) {
        this.draftReviewer = draftReviewer;
        this.actorResolver = actorResolver;
    }

    @Override
    public DraftInvoiceBatchResponse discardDraftInvoicesOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String reason) {
        return DraftInvoiceBatchResponse.of(draftReviewer.discardDraftsOfRun(monthlyRunId, invoiceIds, reason, StatusChangeActor.ofPerson(actorResolver.currentPersonId())));
    }
}
