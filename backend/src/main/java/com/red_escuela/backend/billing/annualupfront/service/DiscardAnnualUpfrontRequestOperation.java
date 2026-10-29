package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountReservationService;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceAwaitingPaymentDiscardService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DiscardAnnualUpfrontRequestOperation implements BillingOperation<DiscardAnnualUpfrontRequestCommand, DiscardedAnnualUpfrontRequest> {
    private static final String DISCARD_REASON = "Pedido de pago anual descartado por el alumno";

    private final AnnualUpfrontQuoteService quoteService;
    private final StudentCurrentAccountService accountService;
    private final EnrollmentDiscountLock enrollmentLock;
    private final AnnualUpfrontDiscountReservationService discountReservations;
    private final InvoiceAwaitingPaymentDiscardService invoices;
    private final AnnualUpfrontRequestRepository requestRepository;
    private final Clock clock;

    public DiscardAnnualUpfrontRequestOperation(AnnualUpfrontQuoteService quoteService, StudentCurrentAccountService accountService, EnrollmentDiscountLock enrollmentLock,
                                                AnnualUpfrontDiscountReservationService discountReservations, InvoiceAwaitingPaymentDiscardService invoices,
                                                AnnualUpfrontRequestRepository requestRepository, Clock clock) {
        this.quoteService = quoteService;
        this.accountService = accountService;
        this.enrollmentLock = enrollmentLock;
        this.discountReservations = discountReservations;
        this.invoices = invoices;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.DISCARD_ANNUAL_UPFRONT_REQUEST;
    }

    @Override
    public Class<DiscardAnnualUpfrontRequestCommand> contextType() {
        return DiscardAnnualUpfrontRequestCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(DiscardAnnualUpfrontRequestCommand command) {
    }

    @Override
    public DiscardedAnnualUpfrontRequest processOperation(DiscardAnnualUpfrontRequestCommand command) {
        BillingEnrollmentFacts facts = quoteService.requireEnrollmentOfCurrentStudent(command.programEnrollmentId());
        enrollmentLock.lockDiscountsOf(EnrollmentReference.ofProgram(command.programEnrollmentId()));
        accountService.findOrOpenAndLockStudentCurrentAccount(facts.studentId(), facts.currency());
        Long invoiceId = requestRepository.findById(command.requestId()).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(command.requestId())).getInvoiceId();
        Invoice invoice = invoices.lockInvoiceAwaitingPayment(invoiceId);
        AnnualUpfrontRequest request = requestRepository.findByIdForUpdate(command.requestId()).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(command.requestId()));
        request.discardCancelledByStudent(LocalDateTime.now(clock));
        invoices.discardInvoiceAwaitingPayment(invoice, DISCARD_REASON, command.actor());
        discountReservations.releaseReservationsOfDiscardedInvoice(invoiceId, command.actor());
        AnnualUpfrontRequest saved = requestRepository.saveAndFlush(request);
        return new DiscardedAnnualUpfrontRequest(saved.getId(), saved.getStatus().name(), saved.getDiscardReason().name(), invoiceId);
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(DiscardAnnualUpfrontRequestCommand command, DiscardedAnnualUpfrontRequest result) {
        return List.of();
    }
}
