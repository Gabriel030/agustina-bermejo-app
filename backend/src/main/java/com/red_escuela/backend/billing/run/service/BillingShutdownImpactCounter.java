package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.service.InvoiceOpenWorkCounter;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.repository.BillingRunRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.shared.domain.BillingShutdownImpact;
import com.red_escuela.backend.shared.domain.BillingShutdownImpactPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class BillingShutdownImpactCounter implements BillingShutdownImpactPort {

    static final List<String> SCHEDULED_JOBS = List.of(
            "ActivateDueConfigurationVersionsJob",
            "ActivateDuePriceVersionsJob",
            "AdmissionApplicationExpiryJob",
            "ExpireAuthorizationRequestsJob",
            "FailedInvoiceRetryJob",
            "IdempotencyRecordCleanupJob",
            "MissedBillingRunAlertJob",
            "MissedMonthlyInvoiceRunAlertJob",
            "MonthlyInvoiceIssuanceJob",
            "MonthlyInvoiceSimulationJob",
            "NightlyBillingJob",
            "OverdueReceiptReviewAlertJob");

    private static final Set<PaymentStatus> UNRECONCILED_PAYMENT_STATUSES = Set.of(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED);
    private static final Set<MonthlyInvoiceRunStatus> MONTHLY_RUNS_IN_PROGRESS =
            Set.of(MonthlyInvoiceRunStatus.RUNNING, MonthlyInvoiceRunStatus.AWAITING_REVIEW);

    private final InvoiceOpenWorkCounter invoiceOpenWorkCounter;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MonthlyInvoiceRunRepository monthlyInvoiceRunRepository;
    private final NightlyBillingRunRepository nightlyBillingRunRepository;
    private final BillingRunRepository billingRunRepository;

    public BillingShutdownImpactCounter(
            InvoiceOpenWorkCounter invoiceOpenWorkCounter,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            MonthlyInvoiceRunRepository monthlyInvoiceRunRepository,
            NightlyBillingRunRepository nightlyBillingRunRepository,
            BillingRunRepository billingRunRepository) {
        this.invoiceOpenWorkCounter = invoiceOpenWorkCounter;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.monthlyInvoiceRunRepository = monthlyInvoiceRunRepository;
        this.nightlyBillingRunRepository = nightlyBillingRunRepository;
        this.billingRunRepository = billingRunRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingShutdownImpact describeBillingShutdownImpact() {
        long runsInProgress = monthlyInvoiceRunRepository.countByStatusIn(MONTHLY_RUNS_IN_PROGRESS)
                + nightlyBillingRunRepository.countByStatus(NightlyBillingRunStatus.RUNNING)
                + billingRunRepository.countByStatus(BillingRunStatus.RUNNING);
        InvoiceOpenWorkCounter.InvoiceOpenWork invoices = invoiceOpenWorkCounter.countOpenWork();
        return new BillingShutdownImpact(
                invoices.open(),
                invoices.overdue(),
                invoices.drafts(),
                invoices.awaitingPayment(),
                invoices.underPaymentReview(),
                paymentRepository.countByStatusIn(UNRECONCILED_PAYMENT_STATUSES),
                refundRepository.countByStatusIn(RefundStatus.PENDING),
                runsInProgress,
                SCHEDULED_JOBS);
    }
}
