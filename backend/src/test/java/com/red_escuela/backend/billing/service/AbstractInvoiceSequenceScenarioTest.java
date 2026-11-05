package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

abstract class AbstractInvoiceSequenceScenarioTest extends AbstractInvoiceSequenceTest {

    @Autowired
    protected BillingConfigurationAlertService alertService;
    @Autowired
    protected ApplicationEventPublisher eventPublisher;
    @Autowired
    protected BillingTestTrigger invoiceGenerationService;

    protected static final YearMonth JAN_2026 = YearMonth.of(2026, 1);
    protected static final YearMonth DEC_2027 = YearMonth.of(2027, 12);

    protected List<BillingConfigurationAlert> openAlerts(Career career) {
        return alertService.listOpen().stream().filter(a -> a.getProgram().getId().equals(career.programId())).toList();
    }

    protected void publishYearBillingConfigured(Career career) {
        transactionTemplate.executeWithoutResult(s -> eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(career.programId())));
    }
}
