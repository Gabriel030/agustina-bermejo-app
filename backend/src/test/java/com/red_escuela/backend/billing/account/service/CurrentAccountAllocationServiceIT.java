package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.account.exception.PaymentAllocationRejectedException;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-089")
@Tag("D-139")
@Tag("D-144")
class CurrentAccountAllocationServiceIT extends AbstractCurrentAccountIT {

    private static final LocalDate TODAY = LocalDate.of(2027, 4, 20);
    private static final int GRACE_DAYS = 5;

    @Autowired
    private CurrentAccountAllocationService allocationService;
    @Autowired
    private PaymentAllocationRepository allocationRepository;

    private BillingInvariantChecker invariants;
    private Long accountId;

    @BeforeEach
    void openAccountAndStartInvariants() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        accountId = newAccountId(newStudentId(), Currency.ARS);
    }

    private Long issue(String amount, LocalDate dueDate, FeeType feeType) {
        return inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE,
                    InvoiceType.ADMINISTRATIVE_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(feeType, "Concepto", YearMonth.of(2027, 3), null, null, null, null, null,
                    "AL:" + System.nanoTime(), ars(amount));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.minusDays(9), dueDate, SNAPSHOT, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, dueDate.minusDays(9));
            return saved.getId();
        });
    }

    private Long issueLateFeeNoteOf(Long originInvoiceId, String amount, LocalDate dueDate) {
        return inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE,
                    InvoiceIssuingReason.MONTHLY_RUN, originInvoiceId);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.LATE_FEE, "Recargo", YearMonth.of(2027, 3), null, null, null, null, null,
                    "LF:" + originInvoiceId, ars(amount));
            Invoice note = InvoiceFactory.createLateFeeDebitNoteDraft(header, line, ACTOR);
            note.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE), dueDate.minusDays(9), dueDate, SNAPSHOT, ACTOR);
            Invoice saved = invoiceRepository.save(note);
            service.recordDebitNoteIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, dueDate.minusDays(9));
            return saved.getId();
        });
    }

    private void markOverdue(Long invoiceId, LocalDate date) {
        inTransaction(() -> {
            invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow().markInvoiceAsOverdue(date, ACTOR);
            return null;
        });
    }

    private void receiveCredit(String amount, LocalDate paidOn) {
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), nextExternalId(), ars(amount), paidOn, "Pago");
            return null;
        });
    }

    private AccountCreditAllocationResult allocateOn(LocalDate businessDate) {
        return inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), businessDate, ACTOR));
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private BigDecimal unallocatedCredit() {
        return amount("select unallocated_credit_amount from student_current_account where id = ?", accountId);
    }

    private List<Map<String, Object>> allocationRows() {
        return jdbc.queryForList("select id, invoice_id, amount, effective_date, allocated_on, reverses_allocation_id from payment_allocation "
                + "where student_current_account_id = ? order by id", accountId);
    }

    @Test
    @Tag("D-140")
    void creditAllocatesFullInvoicesOldestFirstAndKeepsTheDebitNoteAndTheNewerInvoiceUnpaid() {
        Long march = issue("80000.00", LocalDate.of(2027, 3, 10), FeeType.ADMINISTRATIVE_FEE);
        Long marchLateFee = issueLateFeeNoteOf(march, "4000.00", LocalDate.of(2027, 3, 10));
        Long april = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        markOverdue(march, TODAY);
        markOverdue(marchLateFee, TODAY);
        markOverdue(april, TODAY);
        receiveCredit("82000.00", TODAY);

        AccountCreditAllocationResult result = allocateOn(TODAY);

        assertThat(result.allocations()).hasSize(1);
        assertThat(paymentStatusOf(march)).isEqualTo("PAID");
        assertThat(paymentStatusOf(marchLateFee)).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(april)).isEqualTo("OVERDUE");
        assertThat(unallocatedCredit()).isEqualByComparingTo("2000.00");
        assertThat(allocationRows()).hasSize(1);
        assertThat(allocationRows().get(0).get("invoice_id")).isEqualTo(march);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void creditThatDoesNotCoverTheNextInvoiceStaysUnallocatedAndASecondCreditCompletesIt() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("30000.00", TODAY);

        assertThat(allocateOn(TODAY).isEmpty()).isTrue();
        assertThat(paymentStatusOf(invoice)).isEqualTo("PENDING");
        assertThat(unallocatedCredit()).isEqualByComparingTo("30000.00");
        assertThat(allocationRows()).isEmpty();

        receiveCredit("50000.00", TODAY);
        assertThat(allocateOn(TODAY).allocations()).hasSize(1);
        assertThat(paymentStatusOf(invoice)).isEqualTo("PAID");
        assertThat(unallocatedCredit()).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-128")
    void aNewerInvoiceTheStudentWantsToPayCannotJumpTheQueueOfAnOlderOverdueOne() {
        Long february = issue("80000.00", LocalDate.of(2027, 2, 10), FeeType.ADMINISTRATIVE_FEE);
        Long enrollmentFee = issue("50000.00", LocalDate.of(2027, 8, 10), FeeType.ADMINISTRATIVE_FEE);
        markOverdue(february, TODAY);
        receiveCredit("50000.00", TODAY);

        assertThat(allocateOn(TODAY).isEmpty()).isTrue();

        assertThat(paymentStatusOf(february)).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(enrollmentFee)).isEqualTo("PENDING");
        assertThat(unallocatedCredit()).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    void anOverpaymentSettlesTheInvoiceAndTheRestStaysInFavor() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("85000.00", TODAY);

        allocateOn(TODAY);

        assertThat(paymentStatusOf(invoice)).isEqualTo("PAID");
        assertThat(unallocatedCredit()).isEqualByComparingTo("5000.00");
        assertThat(amount("select balance_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("5000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anInvoiceWithAReceiptUnderReviewIsAllocatedLikeAnyOpenInvoice() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        inTransaction(() -> {
            invoiceRepository.findByIdForUpdate(invoice).orElseThrow().markPaymentUnderReview(TODAY, ACTOR, "Comprobante");
            return null;
        });
        receiveCredit("80000.00", TODAY);

        allocateOn(TODAY);

        assertThat(paymentStatusOf(invoice)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    void reversalGoesNewestFirstAndEachInvoiceReturnsToOverdueWithItsOriginalDueDate() {
        Long march = issue("80000.00", LocalDate.of(2027, 3, 10), FeeType.ADMINISTRATIVE_FEE);
        Long april = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("160000.00", LocalDate.of(2027, 3, 9));
        allocateOn(LocalDate.of(2027, 3, 9));
        assertThat(paymentStatusOf(march)).isEqualTo("PAID");
        assertThat(paymentStatusOf(april)).isEqualTo("PAID");

        List<PaymentAllocation> reversals = inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(
                service.lockStudentCurrentAccount(accountId), ars("160000.00"), TODAY, GRACE_DAYS, "Contracargo", ACTOR));

        assertThat(reversals).extracting(PaymentAllocation::getInvoiceId).containsExactly(april, march);
        assertThat(paymentStatusOf(march)).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(april)).isEqualTo("OVERDUE");
        assertThat(unallocatedCredit()).isEqualByComparingTo("160000.00");
        assertThat(allocationRows()).hasSize(4);
        assertThat(allocationRows().stream().filter(row -> row.get("reverses_allocation_id") != null)).hasSize(2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    void reversalOnTheDueDateBoundaryReturnsToPendingAndTheDayAfterTheGraceToOverdue() {
        Long lastDayOfGrace = issue("10.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("10.00", LocalDate.of(2027, 4, 1));
        allocateOn(LocalDate.of(2027, 4, 1));

        inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(accountId), ars("10.00"),
                LocalDate.of(2027, 4, 15), GRACE_DAYS, "Revocación", ACTOR));
        assertThat(paymentStatusOf(lastDayOfGrace)).isEqualTo("PENDING");

        allocateOn(LocalDate.of(2027, 4, 16));
        inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(accountId), ars("10.00"),
                LocalDate.of(2027, 4, 16), GRACE_DAYS, "Revocación", ACTOR));
        assertThat(paymentStatusOf(lastDayOfGrace)).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    void anAllocationIsNeverReversedTwice() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);
        allocateOn(TODAY);
        PaymentAllocation allocation = allocationRepository.findCurrentAllocationByInvoiceId(invoice).orElseThrow();
        inTransaction(() -> allocationService.reverseAllocation(service.lockStudentCurrentAccount(accountId), allocation, TODAY, GRACE_DAYS, "Contracargo", ACTOR));

        assertThatThrownBy(() -> inTransaction(() -> allocationService.reverseAllocation(service.lockStudentCurrentAccount(accountId), allocation, TODAY,
                GRACE_DAYS, "Otra vez", ACTOR)))
                .isInstanceOf(PaymentAllocationRejectedException.class)
                .satisfies(error -> assertThat(((PaymentAllocationRejectedException) error).violations()).extracting("code")
                        .containsExactly("PAYMENT_ALLOCATION_ALREADY_REVERSED"));
        assertThat(allocationRows()).hasSize(2);
    }

    @Test
    @Tag("D-094")
    void theDatabaseRejectsASecondReversalOfTheSameAllocationEvenWithThePolicyOff() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);
        allocateOn(TODAY);
        PaymentAllocation allocation = allocationRepository.findCurrentAllocationByInvoiceId(invoice).orElseThrow();
        insertRawAllocation(invoice, allocation.getId());

        assertThatThrownBy(() -> insertRawAllocation(invoice, allocation.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-136")
    void theDatabaseRejectsASecondCurrentAllocationForTheSameInvoice() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);
        allocateOn(TODAY);

        assertThatThrownBy(() -> insertRawAllocation(invoice, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-094")
    void theDatabaseRejectsAReversalOfAnotherInvoiceOrADifferentAmount() {
        Long first = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        Long second = issue("70000.00", LocalDate.of(2027, 5, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("150000.00", TODAY);
        allocateOn(TODAY);
        PaymentAllocation firstAllocation = allocationRepository.findCurrentAllocationByInvoiceId(first).orElseThrow();

        assertThatThrownBy(() -> insertRawAllocation(second, firstAllocation.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-094")
    void aReversalWithoutEnoughAllocationsToCoverTheCreditIsRejected() {
        issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);
        allocateOn(TODAY);

        assertThatThrownBy(() -> inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(accountId),
                ars("90000.00"), TODAY, GRACE_DAYS, "Contracargo", ACTOR))).isInstanceOf(InsufficientAccountCreditException.class);
        assertThat(allocationRows()).hasSize(1);
    }

    @Test
    void reversingWhenTheCreditAlreadyCoversTheRemovalReversesNothing() {
        issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("90000.00", TODAY);
        allocateOn(TODAY);

        List<PaymentAllocation> reversals = inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(
                service.lockStudentCurrentAccount(accountId), ars("10000.00"), TODAY, GRACE_DAYS, "Contracargo", ACTOR));

        assertThat(reversals).isEmpty();
    }

    @Test
    void theEffectiveDateIsTheDateOfTheCreditThatCompletedTheCoverage() {
        issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("30000.00", LocalDate.of(2027, 3, 5));
        receiveCredit("50000.00", LocalDate.of(2027, 3, 12));

        allocateOn(TODAY);

        Map<String, Object> row = allocationRows().get(0);
        assertThat(row.get("effective_date").toString()).isEqualTo("2027-03-12");
        assertThat(row.get("allocated_on").toString()).isEqualTo(TODAY.toString());
    }

    @Test
    void theEffectiveDateOfASecondInvoiceSkipsTheCreditAlreadyConsumedByTheFirst() {
        issue("50000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        issue("60000.00", LocalDate.of(2027, 5, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("50000.00", LocalDate.of(2027, 3, 5));
        receiveCredit("60000.00", LocalDate.of(2027, 3, 12));

        allocateOn(TODAY);

        assertThat(allocationRows().stream().map(row -> row.get("effective_date").toString())).containsExactly("2027-03-05", "2027-03-12");
    }

    @Test
    void allocatingWithoutCreditOrWithoutOpenInvoicesChangesNothing() {
        assertThat(allocateOn(TODAY).isEmpty()).isTrue();
        issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        assertThat(allocateOn(TODAY).isEmpty()).isTrue();
        assertThat(allocationRows()).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void allocatingTwiceDoesNotAllocateTheSameInvoiceAgain() {
        issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);

        allocateOn(TODAY);
        assertThat(allocateOn(TODAY).isEmpty()).isTrue();

        assertThat(allocationRows()).hasSize(1);
    }

    @Test
    void allocationsPublishTheAllocationEventsAndThePaidEventsOfTheSettledInvoices() {
        Long invoice = issue("80000.00", LocalDate.of(2027, 4, 10), FeeType.ADMINISTRATIVE_FEE);
        receiveCredit("80000.00", TODAY);

        AccountCreditAllocationResult result = allocateOn(TODAY);

        assertThat(result.settledInvoices()).extracting(Invoice::getId).containsExactly(invoice);
        assertThat(result.allocations().get(0).getAmount()).isEqualTo(NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS));
    }

    private void insertRawAllocation(Long invoiceId, Long reversesAllocationId) {
        jdbc.update("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, effective_date, allocated_on, "
                        + "reverses_allocation_id, reason, actor_process, recorded_at) values (?, ?, ?, 'ARS', ?, ?, ?, ?, 'TEST', ?)",
                accountId, invoiceId, jdbc.queryForObject("select total from invoice where id = ?", BigDecimal.class, invoiceId), TODAY, TODAY,
                reversesAllocationId, reversesAllocationId == null ? null : "Prueba", LocalDateTime.now());
    }
}
