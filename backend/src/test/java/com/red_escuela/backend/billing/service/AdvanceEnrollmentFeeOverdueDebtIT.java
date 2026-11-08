package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.exception.AdvanceEnrollmentFeeNotAvailableException;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-523")
@Tag("D-065")
@Tag("D-128")
class AdvanceEnrollmentFeeOverdueDebtIT extends AbstractAdvanceEnrollmentFeeTest {

    private static final int GRACE_DAYS = 5;

    @Autowired
    private ConfigurationVersionRepository configurations;

    private Setup septemberWithGrace() {
        Setup setup = september();
        transactionTemplate.executeWithoutResult(status -> LatePaymentTestFixtures.persistLatePayment(configurations,
                entityManager.getReference(Person.class, setup.career().directorId()), GRACE_DAYS, new BigDecimal("0.01"), 30, 2, LocalDate.of(2027, 9, 1)));
        return setup;
    }

    private Invoice installmentDueOn(Setup setup, LocalDate dueDate) {
        return invoiceOf(setup, dueDate);
    }

    private Invoice invoiceOf(Setup setup, LocalDate dueDate) {
        ProgramEnrollment enrollment = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, setup.enrollment()));
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.from(dueDate.minusMonths(1)))
                .dueOn(dueDate).withBaseAmount(ars(10000)).pending().build();
    }

    private String denialCode(Setup setup) {
        actAsStudentOf(setup.enrollment());
        return ((AdvanceEnrollmentFeeNotAvailableException) catchThrowable(() -> issuer.issueForCurrentStudent(setup.enrollment()))).violations().get(0).code();
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException error) {
            return error;
        }
    }

    @Test
    void anInstallmentPastDueInsideTheGraceNotYetMarkedOverdueDoesNotBlock() {
        Setup setup = septemberWithGrace();
        installmentDueOn(setup, date(2027, 9, 12));
        actAsStudentOf(setup.enrollment());

        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForCurrentStudent(setup.enrollment());

        assertThat(issuance.created()).isTrue();
    }

    @Test
    void anInstallmentPastDueAfterTheGraceBlocksEvenIfTheJobDidNotMarkItOverdue() {
        Setup setup = septemberWithGrace();
        installmentDueOn(setup, date(2027, 9, 5));

        assertThat(denialCode(setup)).isEqualTo("ADVANCE_FEE_OVERDUE_DEBT");
        assertThat(advanceInvoices(setup.enrollment())).isEmpty();
    }

    @Test
    void anInstallmentUnderReviewWithAProtectedReceiptDoesNotBlock() {
        Setup setup = septemberWithGrace();
        Invoice overdue = installmentDueOn(setup, date(2027, 9, 5));
        jdbcTemplate.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", overdue.getId());
        jdbcTemplate.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'receipt.pdf', ?, 'PENDING_REVIEW', 0)",
                overdue.getId(), java.sql.Timestamp.valueOf(date(2027, 9, 4).atTime(10, 0)));
        actAsStudentOf(setup.enrollment());

        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForCurrentStudent(setup.enrollment());

        assertThat(issuance.created()).isTrue();
    }

    @Test
    void anInstallmentMarkedOverdueBlocks() {
        Setup setup = septemberWithGrace();
        BillingTestFixtures.markInvoiceAsOverdue(installmentDueOn(setup, date(2027, 9, 12)));

        assertThat(denialCode(setup)).isEqualTo("ADVANCE_FEE_OVERDUE_DEBT");
        assertThatThrownBy(() -> issuer.issueForCurrentStudent(setup.enrollment())).isInstanceOf(AdvanceEnrollmentFeeNotAvailableException.class);
    }
}
