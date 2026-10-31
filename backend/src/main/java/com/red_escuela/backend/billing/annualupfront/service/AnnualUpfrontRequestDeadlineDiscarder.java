package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountReservationService;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceAwaitingPaymentDiscardService;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AnnualUpfrontRequestDeadlineDiscarder {
    private static final String DISCARD_REASON = "Pedido de pago anual descartado por vencimiento del plazo de pago";
    private static final StatusChangeActor SYSTEM_ACTOR = StatusChangeActor.ofProcess("ANNUAL_UPFRONT_DEADLINE_EXPIRY");

    private final BillingEnrollmentFactsFinder enrollmentLookup;
    private final StudentCurrentAccountService accountService;
    private final EnrollmentDiscountLock enrollmentLock;
    private final AnnualUpfrontDiscountReservationService discountReservations;
    private final InvoiceAwaitingPaymentDiscardService invoices;
    private final AnnualUpfrontRequestRepository requestRepository;
    private final Clock clock;

    public AnnualUpfrontRequestDeadlineDiscarder(BillingEnrollmentFactsFinder enrollmentLookup, StudentCurrentAccountService accountService, EnrollmentDiscountLock enrollmentLock,
                                                 AnnualUpfrontDiscountReservationService discountReservations, InvoiceAwaitingPaymentDiscardService invoices,
                                                 AnnualUpfrontRequestRepository requestRepository, Clock clock) {
        this.enrollmentLookup = enrollmentLookup;
        this.accountService = accountService;
        this.enrollmentLock = enrollmentLock;
        this.discountReservations = discountReservations;
        this.invoices = invoices;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean discardRequestIfDeadlineExpiredWithoutPayment(Long requestId) {
        Long programEnrollmentId = requestRepository.findProgramEnrollmentIdById(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
        BillingEnrollmentFacts facts = enrollmentLookup.findProgramEnrollmentFacts(programEnrollmentId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
        enrollmentLock.lockDiscountsOf(EnrollmentReference.ofProgram(programEnrollmentId));
        accountService.findOrOpenAndLockStudentCurrentAccount(facts.studentId(), facts.currency());
        Long invoiceId = requestRepository.findInvoiceIdById(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
        Invoice invoice = invoices.lockInvoiceAwaitingPayment(invoiceId);
        AnnualUpfrontRequest request = requestRepository.findByIdForUpdate(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
        LocalDateTime now = LocalDateTime.now(clock);
        boolean stillExpirable = request.getStatus() == AnnualUpfrontRequestStatus.AWAITING_PAYMENT && request.getPaymentId() == null
                && invoice.getPaymentDeadline() != null && invoice.getPaymentDeadline().isBefore(now);
        if (!stillExpirable) {
            return false;
        }
        request.discardDeadlineExpired(now);
        invoices.discardInvoiceAwaitingPayment(invoice, DISCARD_REASON, SYSTEM_ACTOR);
        discountReservations.releaseReservationsOfDiscardedInvoice(invoiceId, StatusChangeActor.ofPerson(request.getRequestedByPersonId()));
        requestRepository.saveAndFlush(request);
        return true;
    }
}
