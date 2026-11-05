package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-153")
@Tag("D-260")
@Tag("D-139")
class BankTransferReceiptOperationProperties extends AbstractBankTransferReceiptIT {
    private static final long SEED = 20261021L;
    private static final int SCENARIOS = 25;

    record Attempt(int invoiceMask, int declaredPercent, int dayOffset) {
    }

    private static List<Attempt> attemptsFrom(Random random) {
        int count = 1 + random.nextInt(6);
        List<Attempt> attempts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            attempts.add(new Attempt(1 + random.nextInt(31), 10 + random.nextInt(121), random.nextInt(8) - 3));
        }
        return attempts;
    }

    private String accountSnapshot(Long accountId) {
        Map<String, Object> row = jdbc.queryForMap("select balance_amount, balance_side, unallocated_credit_amount, last_entry_sequence from student_current_account where id = ?",
                accountId);
        return row.toString() + jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId);
    }

    @Test
    void randomUploadsNeverProtectAnInvoiceTwiceNorWithoutAPendingPaymentNorMoveTheAccount() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Random random = new Random(SEED);
        int accepted = 0;
        for (int scenario = 0; scenario < SCENARIOS; scenario++) {
            Debtor debtor = debtor();
            int invoiceCount = 2 + random.nextInt(4);
            List<Long> invoices = new ArrayList<>();
            List<Long> amounts = new ArrayList<>();
            for (int index = 0; index < invoiceCount; index++) {
                long amount = 10000L + 5000L * random.nextInt(15);
                amounts.add(amount);
                invoices.add(invoiceOf(debtor, amount + ".00", LocalDate.of(2027, 3, 10).plusMonths(index)));
            }
            String accountBefore = accountSnapshot(debtor.accountId());
            List<Attempt> attempts = attemptsFrom(random);
            int attemptNumber = 0;
            for (Attempt attempt : attempts) {
                MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 10).plusDays(attempt.dayOffset()));
                List<Long> chosen = new ArrayList<>();
                long total = 0;
                for (int index = 0; index < invoiceCount; index++) {
                    if ((attempt.invoiceMask() & (1 << index)) != 0) {
                        chosen.add(invoices.get(index));
                        total += amounts.get(index);
                    }
                }
                if (chosen.isEmpty()) {
                    continue;
                }
                long declaredCents = Math.max(1, total * attempt.declaredPercent());
                String declared = (declaredCents / 100) + "." + String.format("%02d", declaredCents % 100);
                String operationNumber = "OP-PROP-" + scenario + "-" + attemptNumber++;
                try {
                    submit(command(debtor, chosen, declared, LocalDate.of(2027, 3, 10).plusDays(attempt.dayOffset()), operationNumber));
                    accepted++;
                } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
                    assertThat(expectedRejection).isNotNull();
                }
                assertThat(jdbc.queryForList("select i.id from invoice i where i.payment_status = 'PAYMENT_UNDER_REVIEW' and i.student_current_account_id = ? and not exists ("
                        + "select 1 from payment_checkout_item c join payment p on p.id = c.payment_id where c.invoice_id = i.id and c.marked_under_review and p.status = 'PENDING')",
                        debtor.accountId())).isEmpty();
                assertThat(jdbc.queryForList("select c.invoice_id from payment_checkout_item c join payment p on p.id = c.payment_id where c.marked_under_review and p.status = 'PENDING' "
                        + "and c.student_current_account_id = ? group by c.invoice_id having count(*) > 1", debtor.accountId())).isEmpty();
                assertThat(jdbc.queryForList("select c.invoice_id from payment_checkout_item c join invoice i on i.id = c.invoice_id where c.marked_under_review "
                        + "and i.payment_status <> 'PAYMENT_UNDER_REVIEW' and c.student_current_account_id = ?", debtor.accountId())).isEmpty();
                assertThat(accountSnapshot(debtor.accountId())).isEqualTo(accountBefore);
            }
        }
        assertThat(accepted).isGreaterThan(SCENARIOS / 2);
        invariants.assertAllBillingInvariantsHold();
    }
}
