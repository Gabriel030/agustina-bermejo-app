package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-144")
@Tag("D-165")
class CreditNoteCommandSequencesIT extends AbstractCreditNoteIT {

    private static final int SEQUENCES = 20;
    private static final LocalDate BASE_DATE = LocalDate.of(2027, 4, 1);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation operation;

    @Test
    void anySequenceOfIssuesPaymentsAllocationsAndCreditNotesKeepsTheBillingInvariants() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Random random = new Random(20261009L);
        Long requester = newStaffPerson().getId();
        int accepted = 0;
        int rejected = 0;

        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            Long studentId = newStudentId();
            Long accountId = newAccountId(studentId, Currency.ARS);
            int size = 2 + random.nextInt(12);
            for (int step = 0; step < size; step++) {
                LocalDate date = BASE_DATE.plusDays(random.nextInt(60));
                BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(300_000), 2);
                switch (random.nextInt(4)) {
                    case 0 -> issueInvoiceOfStudent(studentId, accountId, amount.toPlainString(), date);
                    case 1 -> payAccount(accountId, amount.toPlainString(), date);
                    case 2 -> inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), date, ACTOR));
                    default -> {
                        List<Long> invoices = jdbc.queryForList("select id from invoice where student_current_account_id = ? and document_status = 'ISSUED' "
                                + "and payment_status in ('PENDING','OVERDUE','PAID')", Long.class, accountId);
                        if (invoices.isEmpty()) {
                            continue;
                        }
                        Long invoiceId = invoices.get(random.nextInt(invoices.size()));
                        String status = jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
                        BigDecimal creditAmount = amount.min(new BigDecimal("1500.00"));
                        try {
                            pipeline.runOperation(operation, new IssueCreditNoteCommand(invoiceId, CreditNoteReason.BILLING_ERROR, "Error",
                                    List.of(new CreditNoteLineAmount(1, com.red_escuela.backend.shared.domain.Money.of(creditAmount, com.red_escuela.backend.shared.domain.Currency.ARS))), "PAID".equals(status) ? CreditNoteDestination.FREE_CREDIT : CreditNoteDestination.DEBT_OFFSET,
                                    requester, true, null));
                            accepted++;
                        } catch (ConflictException | BadRequestException rejection) {
                            rejected++;
                        }
                    }
                }
                invariants.assertAllBillingInvariantsHold();
            }
        }
        assertThat(accepted).isPositive();
        assertThat(rejected).isNotNegative();
        assertThat(jdbc.queryForList("select 1 from invoice_line where credited_amount + free_credited_amount > amount - discount_amount")).isEmpty();
    }
}
