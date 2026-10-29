package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

@Component
public class RequestAnnualUpfrontCommandAssembler {
    private final BillingActorResolver actorResolver;
    private final AnnualUpfrontQuoteService quoteService;

    public RequestAnnualUpfrontCommandAssembler(BillingActorResolver actorResolver, AnnualUpfrontQuoteService quoteService) {
        this.actorResolver = actorResolver;
        this.quoteService = quoteService;
    }

    public RequestAnnualUpfrontCommand assembleCommandOfCurrentStudent(Long programEnrollmentId, boolean termsAccepted, String termsVersion, String idempotencyKey) {
        quoteService.requireEnrollmentOfCurrentStudent(programEnrollmentId);
        return new RequestAnnualUpfrontCommand(programEnrollmentId, termsAccepted, termsVersion, idempotencyKey, StatusChangeActor.ofPerson(actorResolver.currentPersonId()));
    }
}
