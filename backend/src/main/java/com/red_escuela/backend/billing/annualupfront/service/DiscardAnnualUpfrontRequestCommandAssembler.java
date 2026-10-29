package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

@Component
public class DiscardAnnualUpfrontRequestCommandAssembler {
    private final BillingActorResolver actorResolver;
    private final AnnualUpfrontQuoteService quoteService;
    private final AnnualUpfrontRequestRepository requestRepository;

    public DiscardAnnualUpfrontRequestCommandAssembler(BillingActorResolver actorResolver, AnnualUpfrontQuoteService quoteService, AnnualUpfrontRequestRepository requestRepository) {
        this.actorResolver = actorResolver;
        this.quoteService = quoteService;
        this.requestRepository = requestRepository;
    }

    public DiscardAnnualUpfrontRequestCommand assembleCommandOfCurrentStudent(Long requestId) {
        AnnualUpfrontRequest request = requestRepository.findById(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
        quoteService.requireEnrollmentOfCurrentStudent(request.getProgramEnrollmentId());
        return new DiscardAnnualUpfrontRequestCommand(requestId, request.getProgramEnrollmentId(), StatusChangeActor.ofPerson(actorResolver.currentPersonId()));
    }
}
