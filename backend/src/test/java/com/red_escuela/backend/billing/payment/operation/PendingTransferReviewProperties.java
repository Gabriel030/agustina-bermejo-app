package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-153")
@Tag("D-261")
@Tag("D-318")
@Tag("D-139")
class PendingTransferReviewProperties extends AbstractPendingTransferBridgeIT {
    private static final long SEED = 20261023L;
    private static final int SCENARIOS = 8;
    private static final int STEPS_PER_SCENARIO = 9;

    private final AtomicInteger approvedReceipts = new AtomicInteger();
    private final AtomicInteger rejectedReceipts = new AtomicInteger();
    private final AtomicInteger cashPayments = new AtomicInteger();

    private record Ledger(Debtor debtor, List<Long> invoices, List<Long> amounts) {
    }

    private Ledger ledgerWith(Random random, int invoiceCount) {
        Debtor debtor = debtor();
        List<Long> invoices = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (int index = 0; index < invoiceCount; index++) {
            long amount = 10000L + 5000L * random.nextInt(15);
            amounts.add(amount);
            invoices.add(invoiceOf(debtor, amount + ".00", LocalDate.of(2027, 3, 10).plusMonths(index)));
        }
        return new Ledger(debtor, invoices, amounts);
    }

    private static String pesos(long cents) {
        return (cents / 100) + "." + String.format("%02d", cents % 100);
    }

    private List<BankTransferReceiptSubmissionResult> pendingOf(List<BankTransferReceiptSubmissionResult> submitted) {
        return submitted.stream().filter(result -> "PENDING_REVIEW".equals(receiptStatus(result.receiptId()))).toList();
    }

    private void uploadRandomReceipt(Random random, Ledger ledger, List<BankTransferReceiptSubmissionResult> submitted, int stepId) {
        List<Long> chosen = new ArrayList<>();
        long total = 0;
        int mask = random.nextBoolean() ? 1 << random.nextInt(ledger.invoices().size()) : 1 + random.nextInt((1 << ledger.invoices().size()) - 1);
        for (int index = 0; index < ledger.invoices().size(); index++) {
            if ((mask & (1 << index)) != 0) {
                chosen.add(ledger.invoices().get(index));
                total += ledger.amounts().get(index);
            }
        }
        long declaredCents = Math.max(1, total * (10 + random.nextInt(121)));
        try {
            submitted.add(submit(command(ledger.debtor(), chosen, pesos(declaredCents), LocalDate.now(MutableClock.INSTANCE), "OP-REVIEW-PROP-" + stepId)));
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            assertThat(expectedRejection).isNotNull();
        }
    }

    private void approveRandomPendingReceipt(Random random, List<BankTransferReceiptSubmissionResult> submitted) {
        List<BankTransferReceiptSubmissionResult> pending = pendingOf(submitted);
        if (pending.isEmpty()) {
            return;
        }
        BankTransferReceiptSubmissionResult target = pending.get(random.nextInt(pending.size()));
        PaymentVerification verification = PaymentVerification.none();
        if (random.nextInt(10) < 6) {
            BigDecimal declared = target.declaredAmount().value().amount();
            long verifiedPesos = Math.max(1, declared.longValue() * (50 + random.nextInt(251)) / 100);
            verification = new PaymentVerification(ars(verifiedPesos + ".00"), null, null);
        }
        try {
            approve(target, verification);
            approvedReceipts.incrementAndGet();
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            assertThat(expectedRejection).isNotNull();
        }
    }

    private void rejectRandomPendingReceipt(Random random, List<BankTransferReceiptSubmissionResult> submitted) {
        List<BankTransferReceiptSubmissionResult> pending = pendingOf(submitted);
        if (!pending.isEmpty()) {
            reject(pending.get(random.nextInt(pending.size())), "Rechazo aleatorio");
            rejectedReceipts.incrementAndGet();
        }
    }

    private void registerRandomCash(Random random, Ledger ledger) {
        String amount = (5000L * (1 + random.nextInt(60))) + ".00";
        try {
            registerCash(ledger.debtor(), amount, previewCashOf(ledger.debtor(), amount));
            cashPayments.incrementAndGet();
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            assertThat(expectedRejection).isNotNull();
        }
    }

    private void moveTheClockForwardAndMarkOverdueInvoices(Random random, List<Ledger> ledgers) {
        MutableClock.INSTANCE.setDate(LocalDate.now(MutableClock.INSTANCE).plusDays(1 + random.nextInt(20)));
        for (Ledger ledger : ledgers) {
            for (Long invoiceId : ledger.invoices()) {
                if ("PENDING".equals(paymentStatusOfInvoice(invoiceId))) {
                    overdueTransition.markAsOverdueWhenPastDueDate(invoiceId, LocalDate.now(MutableClock.INSTANCE));
                }
            }
        }
    }

    private void assertReviewInvariantsOf(Ledger ledger) {
        Long accountId = ledger.debtor().accountId();
        assertThat(jdbc.queryForList("select i.id from invoice i where i.payment_status = 'PAYMENT_UNDER_REVIEW' and i.student_current_account_id = ? and not exists ("
                + "select 1 from payment_checkout_item c join payment p on p.id = c.payment_id where c.invoice_id = i.id and c.marked_under_review and p.status = 'PENDING')",
                accountId)).as("invoice under review without a PENDING payment that includes it").isEmpty();
        assertThat(jdbc.queryForList("select c.invoice_id from payment_checkout_item c join payment p on p.id = c.payment_id where c.marked_under_review and p.status = 'PENDING' "
                + "and c.student_current_account_id = ? group by c.invoice_id having count(*) > 1", accountId)).as("invoice protected by two pending payments").isEmpty();
        assertThat(jdbc.queryForList("select c.invoice_id from payment_checkout_item c join payment p on p.id = c.payment_id join invoice i on i.id = c.invoice_id "
                + "where c.marked_under_review and p.status = 'PENDING' and i.payment_status <> 'PAYMENT_UNDER_REVIEW' and c.student_current_account_id = ?", accountId))
                .as("invoice of a pending receipt that was paid or released by someone else").isEmpty();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isNotNegative();
    }

    @Test
    void randomReviewSequencesNeverPayAnInvoiceWaitingForAnotherReceiptAndKeepEveryInvariant() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Random random = new Random(SEED);
        long firstInvoiceId = jdbc.queryForObject("select coalesce(max(id), 0) from invoice", Long.class);
        for (int scenario = 0; scenario < SCENARIOS; scenario++) {
            MutableClock.INSTANCE.setDate(TODAY);
            Ledger first = ledgerWith(random, 3 + random.nextInt(3));
            Ledger second = ledgerWith(random, 2 + random.nextInt(2));
            List<Ledger> ledgers = List.of(first, second);
            List<BankTransferReceiptSubmissionResult> submitted = new ArrayList<>();
            for (int step = 0; step < STEPS_PER_SCENARIO; step++) {
                Ledger target = ledgers.get(random.nextInt(ledgers.size()));
                int stepId = scenario * 100 + step;
                switch (step < 3 ? 0 : random.nextInt(10)) {
                    case 0, 1, 2 -> uploadRandomReceipt(random, target, submitted, stepId);
                    case 3, 4, 5 -> approveRandomPendingReceipt(random, submitted);
                    case 6 -> rejectRandomPendingReceipt(random, submitted);
                    case 7, 8 -> registerRandomCash(random, target);
                    default -> moveTheClockForwardAndMarkOverdueInvoices(random, ledgers);
                }
                ledgers.forEach(this::assertReviewInvariantsOf);
                invariants.assertAllBillingInvariantsHold();
            }
        }
        assertThat(approvedReceipts.get()).isPositive();
        assertThat(rejectedReceipts.get()).isPositive();
        assertThat(cashPayments.get()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from invoice where payment_status = 'OVERDUE' and id > ?", Long.class, firstInvoiceId)).isPositive();
    }
}
