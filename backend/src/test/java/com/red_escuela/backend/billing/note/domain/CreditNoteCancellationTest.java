package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineCredit;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.DUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-127")
@Tag("D-202")
class CreditNoteCancellationTest {

    private static Invoice unpaid() {
        return InvoiceTestData.withId(InvoiceTestData.issuedMonthly("80000.00"), 5L);
    }

    private static CreditNote cancellationOf(Invoice invoice, CreditNoteReason reason) {
        return CreditNote.issueCancellationCreditNoteForInvoice(invoice, reason, "Baja", new CreditNoteNumber("NC-00000001"), DUE_DATE, DUE_DATE, null, ACTOR);
    }

    @Test
    void cancellingCoversTheWholeOutstandingAndMarksTheInvoiceCancelled() {
        Invoice invoice = unpaid();
        CreditNote note = cancellationOf(invoice, CreditNoteReason.CANCELLATION);

        invoice.applyCreditNote(note.toInvoiceCancellationApplication(false), DUE_DATE, ACTOR);

        assertThat(note.getTotalAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(invoice.getLines()).allMatch(line -> line.getChargeKeyReleased() == false);
    }

    @Test
    void theKeysAreReleasedOnlyWhenTheApplicationAsksForIt() {
        Invoice invoice = unpaid();

        invoice.applyCreditNote(cancellationOf(invoice, CreditNoteReason.BILLING_ERROR).toInvoiceCancellationApplication(true), DUE_DATE, ACTOR);

        assertThat(invoice.getLines()).allMatch(line -> line.getChargeKeyReleased());
    }

    @Test
    void aCreditNoteApplicationThatIsNotACancellationCannotReleaseKeys() {
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(CreditNoteApplicationKind.DEBT_REDUCTION, ars("1.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(1, ars("1.00"))), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvoiceReversedAfterAFreeCreditNoteIsCancelledAbsorbingTheFreeCredit() {
        Invoice invoice = unpaid();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);
        invoice.applyCreditNote(new InvoiceCreditNoteApplication(CreditNoteApplicationKind.DEBT_REDUCTION, ars("0.00"), ars("10000.00"),
                List.of(new InvoiceLineCredit(1, ars("10000.00")))), DUE_DATE, ACTOR);
        invoice.reversePaymentAllocation(DUE_DATE, 0, ACTOR, "Contracargo");
        CreditNote note = cancellationOf(invoice, CreditNoteReason.BILLING_ERROR);

        invoice.applyCreditNote(note.toInvoiceCancellationApplication(true), DUE_DATE, ACTOR);

        assertThat(note.getTotalAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getLines().get(0).getFreeCreditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aPaidInvoiceHasNoCancellationNote() {
        Invoice invoice = unpaid();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> cancellationOf(invoice, CreditNoteReason.CANCELLATION)).isInstanceOf(IllegalArgumentException.class);
    }
}
