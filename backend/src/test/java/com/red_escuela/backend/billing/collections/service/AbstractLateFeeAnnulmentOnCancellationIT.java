package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractLateFeeAnnulmentOnCancellationIT extends AbstractLateFeeCorrectionIT {

    @Autowired
    protected BillingOperationPipeline pipeline;
    @Autowired
    protected CancelInvoiceOperation cancelInvoiceOperation;

    protected Invoice marchInstallmentWithTheMarchAndAprilCloses(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 5, 1));
        assertThat(summaryOf(origin)).containsExactly(year + "-03-12.." + year + "-03-31:20:4000.00", year + "-04-01.." + year + "-04-30:30:6000.00");
        MutableClock.INSTANCE.setDate(d(year, 5, 2));
        return origin;
    }

    protected CreditNote cancel(Invoice installment, CreditNoteReason reason) {
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(installment.getId(), reason, "Cuota con recargos", person.getId(), true);
        return pipeline.runOperation(cancelInvoiceOperation, command).resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("Unexpected authorization " + id));
    }

    protected String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    protected BigDecimal unallocatedCreditOfAccount(Invoice origin) {
        return jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, origin.getStudentCurrentAccountId());
    }

    protected BigDecimal balanceOfAccount(Invoice origin) {
        return jdbc.queryForObject("select balance_amount from student_current_account where id = ?", BigDecimal.class, origin.getStudentCurrentAccountId());
    }

    protected long countOfCreditNotesOf(Long... invoiceIds) {
        long total = 0;
        for (Long invoiceId : invoiceIds) {
            total += creditNotesOf(invoiceId).size();
        }
        return total;
    }

    protected Map<String, Object> onlyCreditNoteOf(Long invoiceId) {
        List<Map<String, Object>> notes = creditNotesOf(invoiceId);
        assertThat(notes).hasSize(1);
        return notes.get(0);
    }

    protected long releasedChargeKeysOf(Long invoiceId) {
        return jdbc.queryForObject("select count(charge_key_released_at) from invoice_line where invoice_id = ?", Long.class, invoiceId);
    }

    protected long liveLateFeeDebitNotesOf(Invoice origin) {
        return jdbc.queryForObject("select count(*) from late_fee_charge c join invoice d on d.id = c.debit_note_id where c.origin_invoice_id = ? and d.payment_status <> 'CANCELLED'",
                Long.class, origin.getId());
    }

    protected void assertNothingWasCancelled(Invoice origin, Long... debitNoteIds) {
        assertThat(paymentStatusOf(origin.getId())).isEqualTo("OVERDUE");
        assertThat(countOfCreditNotesOf(origin.getId())).isZero();
        for (Long debitNoteId : debitNoteIds) {
            assertThat(paymentStatusOf(debitNoteId)).isNotEqualTo("CANCELLED");
            assertThat(countOfCreditNotesOf(debitNoteId)).isZero();
        }
    }
}
