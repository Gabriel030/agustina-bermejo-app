package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountReservationService;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.operation.AwaitingPaymentInvoiceIssuer;
import com.red_escuela.backend.billing.invoice.service.InvoiceAwaitingPaymentDiscardService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AnnualUpfrontRequestPaymentTracker {
    private static final String PAYMENT_REJECTED_REASON = "Comprobante del pago anual rechazado";

    private final AnnualUpfrontRequestRepository requestRepository;
    private final EnrollmentDiscountLock enrollmentLock;
    private final AnnualUpfrontDiscountReservationService discountReservations;
    private final InvoiceAwaitingPaymentDiscardService invoices;
    private final AwaitingPaymentInvoiceIssuer invoiceIssuer;
    private final Clock clock;

    public AnnualUpfrontRequestPaymentTracker(AnnualUpfrontRequestRepository requestRepository, EnrollmentDiscountLock enrollmentLock,
                                              AnnualUpfrontDiscountReservationService discountReservations, InvoiceAwaitingPaymentDiscardService invoices,
                                              AwaitingPaymentInvoiceIssuer invoiceIssuer, Clock clock) {
        this.requestRepository = requestRepository;
        this.enrollmentLock = enrollmentLock;
        this.discountReservations = discountReservations;
        this.invoices = invoices;
        this.invoiceIssuer = invoiceIssuer;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AnnualUpfrontRequestPaymentFacts> lockInvoiceAndFindItsRequest(Long invoiceId) {
        invoices.lockInvoiceAwaitingPayment(invoiceId);
        return requestRepository.findByInvoiceId(invoiceId).map(AnnualUpfrontRequestPaymentFacts::of);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEnrollmentDiscountsOfInvoiceRequest(Long invoiceId) {
        requestRepository.findByInvoiceId(invoiceId).ifPresent(request -> enrollmentLock.lockDiscountsOf(EnrollmentReference.ofProgram(request.getProgramEnrollmentId())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void startPaymentReview(Long invoiceId, Long paymentId) {
        AnnualUpfrontRequest request = lockRequestOfInvoice(invoiceId);
        request.startPaymentReview(paymentId);
        requestRepository.saveAndFlush(request);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issueInvoiceAndConfirmApprovedPayment(Long invoiceId, Long paymentId, Long studentId, LocalDate issueDate, StatusChangeActor actor) {
        Invoice invoice = invoices.lockInvoiceAwaitingPayment(invoiceId);
        AnnualUpfrontRequest request = lockRequestOfInvoice(invoiceId);
        invoiceIssuer.issueInvoiceAwaitingPayment(invoice, studentId, issueDate, actor);
        request.confirmApprovedPayment(paymentId, LocalDateTime.now(clock));
        requestRepository.saveAndFlush(request);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void discardRejectedPayment(Long invoiceId, Long paymentId, StatusChangeActor actor) {
        Invoice invoice = invoices.lockInvoiceAwaitingPayment(invoiceId);
        AnnualUpfrontRequest request = lockRequestOfInvoice(invoiceId);
        request.discardRejectedPayment(paymentId, LocalDateTime.now(clock));
        invoices.discardInvoiceAwaitingPayment(invoice, PAYMENT_REJECTED_REASON, actor);
        discountReservations.releaseReservationsOfDiscardedInvoice(invoiceId, actor);
        requestRepository.saveAndFlush(request);
    }

    private AnnualUpfrontRequest lockRequestOfInvoice(Long invoiceId) {
        Long requestId = requestRepository.findByInvoiceId(invoiceId).map(AnnualUpfrontRequest::getId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(invoiceId));
        return requestRepository.findByIdForUpdate(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
    }
}
