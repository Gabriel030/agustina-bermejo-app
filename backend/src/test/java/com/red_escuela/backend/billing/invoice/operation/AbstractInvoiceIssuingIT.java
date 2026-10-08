package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingOperationRunner;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractInvoiceIssuingIT extends AbstractIntegrationTest {

    protected static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST_ISSUING");

    @Autowired
    protected InvoiceIssuingOperationRunner runner;
    @Autowired
    protected IssuingScenarioFactory factory;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected StudentCurrentAccountRepository accountRepository;
    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected BillingInvariantChecker invariants;

    @BeforeEach
    void startInvariants() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        today(LocalDate.of(2027, 3, 1));
    }

    protected void today(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
    }

    protected InvoiceIssuingRequest installment(IssuingScenarioFactory.Offering offering, IssuingScenarioFactory.Learner learner, YearMonth period,
                                                LocalDate dueDate, LocalDate pricingDate) {
        return new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, null, FeeType.MONTHLY_INSTALLMENT,
                period, dueDate, pricingDate, InvoiceIssuingReason.MONTHLY_RUN, learner.selectionId(), null, null, false, null, null, null, null, null, ACTOR);
    }

    protected InvoiceIssuingRequest enrollmentFee(IssuingScenarioFactory.Offering offering, IssuingScenarioFactory.Learner learner, Long templateId, int slot,
                                                  YearMonth period, LocalDate dueDate, Long cohortId, boolean entry, InvoiceIssuingReason reason) {
        return new InvoiceIssuingRequest(learner.studentId(), reason == InvoiceIssuingReason.PROGRAM_ADMISSION ? null : learner.programEnrollmentId(), null,
                offering.programId(), null, cohortId, FeeType.ENROLLMENT_FEE, period, dueDate, dueDate, reason, learner.selectionId(), templateId, slot, entry,
                null, null, null, null, null, ACTOR);
    }

    protected InvoiceIssuingRequest adHoc(IssuingScenarioFactory.Offering offering, IssuingScenarioFactory.Learner learner, FeeType feeType, String amount, String key) {
        return new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, null, feeType,
                YearMonth.of(2027, 3), LocalDate.of(2027, 3, 20), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false,
                NonNegativeMoney.of(new BigDecimal(amount), offering.currency()), "Concepto " + feeType, null, null, key, ACTOR);
    }

    protected Invoice issued(InvoiceIssuingResult result) {
        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        Long id = ((InvoiceIssuingResult.InvoiceIssued) result).invoice().getId();
        return transactionTemplate.execute(status -> {
            Invoice invoice = invoiceRepository.findInvoiceWithLinesById(id).orElseThrow();
            invoice.getLines().forEach(line -> line.getDiscount());
            return invoice;
        });
    }

    protected List<Map<String, Object>> entriesOf(Long studentId, Currency currency) {
        return jdbc.queryForList("""
                select e.sequence_number, e.direction, e.amount, e.running_balance_amount, e.running_balance_side, e.invoice_id
                from current_account_entry e join student_current_account a on a.id = e.student_current_account_id
                where a.student_id = ? and a.currency = ? order by e.sequence_number
                """, studentId, currency.name());
    }

    protected StudentCurrentAccount accountOf(Long studentId, Currency currency) {
        return accountRepository.findByStudentIdAndCurrency(studentId, currency).orElseThrow();
    }

    protected long countRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
