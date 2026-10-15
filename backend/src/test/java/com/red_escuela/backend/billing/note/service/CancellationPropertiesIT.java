package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
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

@Tag("B2-32")
@Tag("D-127")
@Tag("D-144")
@Tag("D-245")
@SlowTest(reason = "Property test that generates random sequences of issues, payments, credit notes and cancellations")
class CancellationPropertiesIT extends AbstractCreditNoteIT {

    private static final int SEQUENCES = 20;
    private static final LocalDate BASE_DATE = LocalDate.of(2027, 4, 1);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation issueOperation;
    @Autowired
    private CancelInvoiceOperation cancelOperation;

    @Test
    void anySequenceOfIssuesPaymentsCreditNotesAndCancellationsKeepsTheBillingInvariants() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Random random = new Random(20261010L);
        Long actor = newStaffPerson().getId();
        CreditNoteReason[] cancellationReasons = {CreditNoteReason.BILLING_ERROR, CreditNoteReason.CANCELLATION, CreditNoteReason.LATE_FEE_ANNULMENT};
        int cancelled = 0;

        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            Long studentId = newStudentId();
            Long accountId = newAccountId(studentId, Currency.ARS);
            int size = 3 + random.nextInt(12);
            for (int step = 0; step < size; step++) {
                LocalDate date = BASE_DATE.plusDays(random.nextInt(60));
                BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(300_000), 2);
                switch (random.nextInt(5)) {
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
                        try {
                            if (random.nextBoolean()) {
                                pipeline.runOperation(cancelOperation, CancelInvoiceCommand.byPerson(invoiceId, cancellationReasons[random.nextInt(3)], "Cancelación", actor, true));
                                cancelled++;
                            } else {
                                String status = jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
                                pipeline.runOperation(issueOperation, new IssueCreditNoteCommand(invoiceId, CreditNoteReason.BILLING_ERROR, "Error",
                                        List.of(new CreditNoteLineAmount(1, Money.of(amount.min(new BigDecimal("1500.00")), Currency.ARS))),
                                        "PAID".equals(status) ? CreditNoteDestination.FREE_CREDIT : CreditNoteDestination.DEBT_OFFSET, actor, true, null));
                            }
                        } catch (ConflictException | BadRequestException rejection) {
                            assertThat(rejection.getMessage()).isNotBlank();
                        }
                    }
                }
                invariants.assertAllBillingInvariantsHold();
            }
        }
        assertThat(cancelled).isPositive();
        assertThat(jdbc.queryForList("select 1 from invoice_line where credited_amount + free_credited_amount > amount - discount_amount")).isEmpty();
        assertThat(jdbc.queryForList("select 1 from invoice where payment_status = 'CANCELLED' and (paid_amount <> 0 or outstanding_amount <> 0 or credited_amount <> total)")).isEmpty();
        assertThat(jdbc.queryForList("select 1 from invoice i where payment_status = 'CANCELLED' and not exists (select 1 from credit_note n where n.invoice_id = i.id)")).isEmpty();
    }
}
