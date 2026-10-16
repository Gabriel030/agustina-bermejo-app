package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-134")
@Tag("D-139")
class InvoiceIssuingWithAccountCreditIT extends AbstractInvoiceIssuingIT {

    @Autowired
    private StudentCurrentAccountService accountService;

    private IssuingScenarioFactory.Offering program() {
        return factory.newProgram(Currency.ARS, 80000, 50000);
    }

    private void creditAccount(Long studentId, String amount) {
        transactionTemplate.executeWithoutResult(status -> {
            StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS);
            accountService.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId()), ars(amount), LocalDate.of(2027, 3, 1), "Pago");
        });
    }

    private BigDecimal unallocatedCreditOf(Long studentId) {
        return jdbc.queryForObject("select unallocated_credit_amount from student_current_account where student_id = ? and currency = 'ARS'", BigDecimal.class, studentId);
    }

    @Test
    void creditInFavorPaysTheNextIssuedInvoiceInTheSameOperation() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        creditAccount(learner.studentId(), "100000.00");

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, FeeType.EXAM_FEE, "80000.00", "d134-" + learner.studentId())));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoice.getId())).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(learner.studentId())).isEqualByComparingTo("20000.00");
        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ?", Long.class, invoice.getId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void creditThatDoesNotCoverTheIssuedInvoiceLeavesItPendingAndKeepsTheCredit() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        creditAccount(learner.studentId(), "30000.00");

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, FeeType.EXAM_FEE, "80000.00", "d139-" + learner.studentId())));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoice.getId())).isEqualTo("PENDING");
        assertThat(unallocatedCreditOf(learner.studentId())).isEqualByComparingTo("30000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void creditSettlesTheOldestOverdueInvoiceBeforeTheNewOne() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        Invoice older = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, FeeType.EXAM_FEE, "50000.00", "old-" + learner.studentId())));
        creditAccount(learner.studentId(), "50000.00");

        Invoice newer = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, FeeType.RETAKEN_SUBJECT_FEE, "50000.00", "new-" + learner.studentId())));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, older.getId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, newer.getId())).isEqualTo("PENDING");
        invariants.assertAllBillingInvariantsHold();
    }
}
