package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-144")
@Tag("D-136")
@Tag("D-089")
class CurrentAccountAllocationCommandSequencesIT extends AbstractCurrentAccountIT {

    private static final LocalDate BASE_DATE = LocalDate.of(2027, 4, 1);
    private static final int SEQUENCES = 25;

    private enum Kind {
        ISSUE_INVOICE,
        RECEIVE_CREDIT,
        ALLOCATE_CREDIT,
        REVERSE_CREDIT
    }

    private record AllocationCommand(Kind kind, BigDecimal amount, int dayOffset) {
    }

    @Autowired
    private CurrentAccountAllocationService allocationService;

    private static List<AllocationCommand> randomSequence(Random random) {
        int size = 1 + random.nextInt(14);
        List<AllocationCommand> commands = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(500_000), 2);
            commands.add(new AllocationCommand(kind, amount, random.nextInt(61)));
        }
        return commands;
    }

    @Test
    void anySequenceOfIssuesCreditsAllocationsAndReversalsKeepsTheBillingInvariants() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Random random = new Random(20261009L);

        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            Long accountId = newAccountId(newStudentId(), Currency.ARS);
            BigDecimal creditedInTotal = BigDecimal.ZERO;
            for (AllocationCommand command : randomSequence(random)) {
                LocalDate date = BASE_DATE.plusDays(command.dayOffset());
                switch (command.kind()) {
                    case ISSUE_INVOICE -> issueAdministrativeFeeWithLedgerDebit(accountId, command.amount().toPlainString(), date);
                    case RECEIVE_CREDIT -> {
                        inTransaction(() -> {
                            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars(command.amount().toPlainString())), ars(command.amount().toPlainString()), date, "Pago");
                            return null;
                        });
                        creditedInTotal = creditedInTotal.add(command.amount());
                    }
                    case ALLOCATE_CREDIT -> inTransaction(() ->
                            allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), date, ACTOR));
                    case REVERSE_CREDIT -> {
                        BigDecimal toRemove = command.amount().min(creditedInTotal);
                        if (toRemove.signum() > 0) {
                            inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(accountId),
                                    ars(toRemove.toPlainString()), date, 5, "Contracargo", ACTOR));
                        }
                    }
                }
                invariants.assertAllBillingInvariantsHold();
                assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isNotNegative();
                assertThat(jdbc.queryForObject("select count(*) from (select a.invoice_id from payment_allocation a where a.reverses_allocation_id is null "
                        + "and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id) and a.student_current_account_id = ? "
                        + "group by a.invoice_id having count(*) > 1) duplicated", Long.class, accountId)).isZero();
            }
        }
    }
}
