package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.api.CertificateEnrollmentFeeCheck;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionStatus;

import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateEnrollmentFeeCheckTest extends AbstractInvoiceSequenceTest {

    @Autowired
    private CertificateEnrollmentFeeCheck feeCheck;

    private Long unpaidFeeEnrollment() {
        Career career = newCareer();
        newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 2, 5));
        return newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
    }

    @Test
    void unpaidEnrollmentFeeIsReportedAsNotSettled() {
        Long enrollmentId = unpaidFeeEnrollment();

        assertThat(feeCheck.isProgramEnrollmentFeeSettled(enrollmentId)).isFalse();
    }

    @Test
    void paidEnrollmentFeeIsReportedAsSettled() {
        Long enrollmentId = unpaidFeeEnrollment();
        feeCheck.isProgramEnrollmentFeeSettled(enrollmentId);
        markAllEnrollmentFees(enrollmentId, InvoicePaymentStatus.PAID);

        assertThat(feeCheck.isProgramEnrollmentFeeSettled(enrollmentId)).isTrue();
    }

    @Test
    void unpaidEnrollmentFeeDoesNotMarkTheCallersTransactionAsRollbackOnly() {
        Long enrollmentId = unpaidFeeEnrollment();
        AtomicBoolean rollbackOnly = new AtomicBoolean(true);
        AtomicBoolean settled = new AtomicBoolean(true);

        transactionTemplate.executeWithoutResult((TransactionStatus status) -> {
            settled.set(feeCheck.isProgramEnrollmentFeeSettled(enrollmentId));
            rollbackOnly.set(status.isRollbackOnly());
        });

        assertThat(settled).isFalse();
        assertThat(rollbackOnly).isFalse();
    }
}
