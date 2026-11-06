package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.run.dto.DraftInvoiceBatchResponse;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftReviewer;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Collection;

@UseCase
public class ApproveDraftInvoicesService implements ApproveDraftInvoicesUseCase {
    private final MonthlyRunDraftReviewer draftReviewer;
    private final BillingActorResolver actorResolver;

    public ApproveDraftInvoicesService(MonthlyRunDraftReviewer draftReviewer, BillingActorResolver actorResolver) {
        this.draftReviewer = draftReviewer;
        this.actorResolver = actorResolver;
    }

    @Override
    public DraftInvoiceBatchResponse approveDraftInvoicesOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String idempotencyKey) {
        return DraftInvoiceBatchResponse.of(draftReviewer.approveDraftsOfRun(monthlyRunId, invoiceIds, idempotencyKey, StatusChangeActor.ofPerson(actorResolver.currentPersonId())));
    }
}
