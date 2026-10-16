package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-089")
@Tag("D-144")
class BridgePaymentNowCreditsAndAllocatesIT extends AbstractCurrentAccountIT {

    @Autowired
    private CurrentAccountAllocationService allocationService;

    private BillingInvariantChecker invariants;
    private Long accountId;
    private Person staff;

    @BeforeEach
    void openAccountAndStartInvariants() {
        MutableClock.INSTANCE.setDateTime(LocalDate.of(2027, 4, 20).atTime(10, 0));
        invariants = BillingInvariantChecker.startingNow(jdbc);
        accountId = newAccountId(newStudentId(), Currency.ARS);
        staff = newStaffPerson();
    }

    private Long payByBankTransfer(Long invoiceId) {
        return BillingTestFixtures.registerApprovedBankTransferPayment(invoiceId, staff.getId());
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private List<Map<String, Object>> allocationRows() {
        return jdbc.queryForList("select invoice_id, amount, effective_date, allocated_on, reverses_allocation_id, actor_person_id from payment_allocation "
                + "where student_current_account_id = ? order by id", accountId);
    }

    @Test
    void aBankTransferPaymentCreditsTheAccountAndTheAllocationServiceSettlesTheInvoice() {
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 4, 30));

        payByBankTransfer(invoiceId);

        assertThat(paymentStatusOf(invoiceId)).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'",
                Long.class, accountId)).isEqualTo(1L);
        List<Map<String, Object>> rows = allocationRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("invoice_id")).isEqualTo(invoiceId);
        assertThat(rows.get(0).get("reverses_allocation_id")).isNull();
        assertThat(rows.get(0).get("actor_person_id")).isEqualTo(staff.getId());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-128")
    void aPaymentOnANewerInvoiceWhileAnOlderOneIsOverdueLeavesTheCreditUnallocated() {
        Long february = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 2, 10));
        Long enrollmentFee = issueAdministrativeFeeWithLedgerDebit(accountId, "50000.00", LocalDate.of(2027, 5, 10));
        inTransaction(() -> {
            invoiceRepository.findByIdForUpdate(february).orElseThrow().markInvoiceAsOverdue(LocalDate.of(2027, 4, 20), ACTOR);
            return null;
        });

        payByBankTransfer(enrollmentFee);

        assertThat(paymentStatusOf(february)).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(enrollmentFee)).isEqualTo("PENDING");
        assertThat(allocationRows()).isEmpty();
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-089")
    void theSourcesOfAPaymentNeverAddUpToMoreThanThePaymentWhenOlderCreditAlsoSettlesInvoices() {
        issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 3, 10));
        Long second = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 4, 10));
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId), ars("80000.00"), LocalDate.of(2027, 3, 9), "Pago previo");
            return null;
        });

        Long paymentId = payByBankTransfer(second);

        assertThat(allocationRows()).hasSize(2);
        assertThat(jdbc.queryForObject("select coalesce(sum(amount), 0) from payment_allocation_source where payment_id = ?", java.math.BigDecimal.class, paymentId))
                .isEqualByComparingTo("80000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-144")
    void theAllocationDatesAreTheLocalBusinessDayEvenWhenTheJvmRunsInUtcAndTheUtcDayIsDifferent() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            MutableClock.INSTANCE.setInstant(Instant.parse("2027-04-21T01:30:00Z"));
            Long late = issueAdministrativeFeeWithLedgerDebit(accountId, "10.00", LocalDate.of(2027, 5, 30));
            payByBankTransfer(late);
            MutableClock.INSTANCE.setInstant(Instant.parse("2027-04-20T03:30:00Z"));
            Long early = issueAdministrativeFeeWithLedgerDebit(accountId, "20.00", LocalDate.of(2027, 5, 30));
            payByBankTransfer(early);
        } finally {
            TimeZone.setDefault(previous);
        }

        List<Map<String, Object>> rows = allocationRows();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("effective_date").toString()).containsExactly("2027-04-20", "2027-04-20");
        assertThat(rows).extracting(row -> row.get("allocated_on").toString()).containsExactly("2027-04-20", "2027-04-20");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    @Tag("D-136")
    void aPaymentAndAReversalOnTheSameAccountAtTheSameTimeNeverDeadlockAndKeepTheInvariants() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long roundAccountId = newAccountId(newStudentId(), Currency.ARS);
            issueAdministrativeFeeWithLedgerDebit(roundAccountId, "80000.00", LocalDate.of(2027, 3, 10));
            Long second = issueAdministrativeFeeWithLedgerDebit(roundAccountId, "80000.00", LocalDate.of(2027, 4, 10));
            inTransaction(() -> {
                service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(roundAccountId), BillingTestFixtures.seedApprovedPaymentRow(roundAccountId), ars("80000.00"), LocalDate.of(2027, 3, 9), "Pago");
                return allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(roundAccountId), LocalDate.of(2027, 3, 9), ACTOR);
            });
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> payment = executor.submit(() -> {
                    start.await();
                    return payByBankTransfer(second);
                });
                Future<?> reversal = executor.submit(() -> {
                    start.await();
                    return inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(roundAccountId),
                            ars("80000.00"), LocalDate.of(2027, 4, 20), 5, "Contracargo", ACTOR));
                });
                start.countDown();
                payment.get(60, TimeUnit.SECONDS);
                reversal.get(60, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertThat(amount("select unallocated_credit_amount + coalesce((select sum(case when reverses_allocation_id is null then amount else -amount end) "
                    + "from payment_allocation where student_current_account_id = ?), 0) from student_current_account where id = ?", roundAccountId, roundAccountId))
                    .isEqualByComparingTo("160000.00");
        }
        invariants.assertAllBillingInvariantsHold();
    }
}
