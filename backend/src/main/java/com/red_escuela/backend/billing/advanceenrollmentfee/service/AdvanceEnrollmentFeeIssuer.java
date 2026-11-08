package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeRequestAssembler.AdvanceEnrollmentFeeRequest;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.stereotype.Component;

@Component
public class AdvanceEnrollmentFeeIssuer {
    private final AdvanceEnrollmentFeeRequestAssembler assembler;
    private final InvoiceIssuingService invoiceIssuingService;
    private final CurrentStudentService currentStudentService;
    private final BillingActorResolver actorResolver;

    public AdvanceEnrollmentFeeIssuer(AdvanceEnrollmentFeeRequestAssembler assembler, InvoiceIssuingService invoiceIssuingService,
                                      CurrentStudentService currentStudentService, BillingActorResolver actorResolver) {
        this.assembler = assembler;
        this.invoiceIssuingService = invoiceIssuingService;
        this.currentStudentService = currentStudentService;
        this.actorResolver = actorResolver;
    }

    public AdvanceEnrollmentFeeIssuance issueForCurrentStudent(Long enrollmentId) {
        return issue(assembler.assembleForStudent(enrollmentId, currentStudentService.resolveCurrentStudent().getId(), requestedBy()));
    }

    public AdvanceEnrollmentFeeIssuance issueForStaff(Long enrollmentId) {
        return issue(assembler.assembleForStaff(enrollmentId, requestedBy()));
    }

    private StatusChangeActor requestedBy() {
        return StatusChangeActor.ofPerson(actorResolver.currentPersonId());
    }

    private AdvanceEnrollmentFeeIssuance issue(AdvanceEnrollmentFeeRequest request) {
        return request.existingInvoiceId().map(AdvanceEnrollmentFeeIssuance::existing)
                .orElseGet(() -> issue(request.issuing().orElseThrow()));
    }

    private AdvanceEnrollmentFeeIssuance issue(InvoiceIssuingRequest request) {
        return switch (invoiceIssuingService.issueInvoiceInNewTransaction(request)) {
            case InvoiceIssuingResult.InvoiceIssued issued -> AdvanceEnrollmentFeeIssuance.created(issued.invoice().getId());
            case InvoiceIssuingResult.InvoiceAlreadyIssued already -> AdvanceEnrollmentFeeIssuance.existing(already.invoiceId());
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
        };
    }

    public record AdvanceEnrollmentFeeIssuance(Long invoiceId, boolean created) {

        static AdvanceEnrollmentFeeIssuance created(Long invoiceId) {
            return new AdvanceEnrollmentFeeIssuance(invoiceId, true);
        }

        static AdvanceEnrollmentFeeIssuance existing(Long invoiceId) {
            return new AdvanceEnrollmentFeeIssuance(invoiceId, false);
        }
    }
}
