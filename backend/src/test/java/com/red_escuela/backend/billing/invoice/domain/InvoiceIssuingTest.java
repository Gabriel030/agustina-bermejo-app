package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent;
import com.red_escuela.backend.billing.invoice.exception.DueDateBeforeIssueDateException;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoiceDocumentStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-057")
@Tag("D-036")
class InvoiceIssuingTest {

    @Test
    void issuingAMonthlyInstallmentFreezesNumberDatesAndSnapshotAndStartsThePaymentCycle() {
        Invoice invoice = withId(monthlyDraft("80000.00"), 10L);

        invoice.issueInvoice(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 1), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);

        assertThat(invoice.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.ISSUED);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getInvoiceNumber().value()).isEqualTo("F-00000001");
        assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.of(2027, 3, 3));
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2027, 3, 13));
        assertThat(invoice.getStudentSnapshot()).isEqualTo(SNAPSHOT);
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getStatusChanges()).hasSize(3);
        assertThat(invoice.getStatusChanges().get(1)).satisfies(change -> {
            assertThat(change.getStatusKind()).isEqualTo(InvoiceStatusKind.DOCUMENT);
            assertThat(change.getFromStatus()).isEqualTo("DRAFT");
            assertThat(change.getToStatus()).isEqualTo("ISSUED");
            assertThat(change.getBusinessDate()).isEqualTo(ISSUE_DATE);
        });
        assertThat(invoice.getStatusChanges().get(2)).satisfies(change -> {
            assertThat(change.getStatusKind()).isEqualTo(InvoiceStatusKind.PAYMENT);
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo("PENDING");
            assertThat(change.getEvent()).isEqualTo("ISSUED_WITH_AMOUNT_DUE");
        });
        assertThat(invoice.pullRecordedDomainEvents()).containsExactly(
                new InvoiceIssuedEvent(10L, 1L, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, ars("80000.00"), ISSUE_DATE, DUE_DATE));
        assertThat(invoice.pullRecordedDomainEvents()).isEmpty();
    }

    @Test
    void aDueDateBeforeTheIssueDateIsRejectedAndTheInvoiceStaysADraft() {
        Invoice invoice = monthlyDraft("80000.00");

        assertThatThrownBy(() -> issue(invoice, LocalDate.of(2027, 3, 3), LocalDate.of(2027, 3, 2))).isInstanceOf(DueDateBeforeIssueDateException.class);

        assertThat(invoice.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
        assertThat(invoice.getInvoiceNumber()).isNull();
        assertThat(invoice.getIssueDate()).isNull();
        assertThat(invoice.getStatusChanges()).hasSize(1);
    }

    @Test
    void aDueDateEqualToTheIssueDateIsAccepted() {
        Invoice invoice = monthlyDraft("80000.00");

        issue(invoice, ISSUE_DATE, ISSUE_DATE);

        assertThat(invoice.getDueDate()).isEqualTo(ISSUE_DATE);
    }

    @Test
    void anInvoiceWithoutLinesCannotBeIssued() {
        Invoice invoice = annualDraft(0);

        assertThatThrownBy(() -> issue(invoice)).isInstanceOf(IllegalInvoiceDocumentStatusTransitionException.class);
        assertThat(invoice.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
    }

    @Test
    void theNumberMustBelongToTheDocumentType() {
        Invoice invoice = monthlyDraft("10.00");

        assertThatThrownBy(() -> invoice.issueInvoice(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE, 1), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDebitNoteTakesADebitNoteNumber() {
        Invoice debitNote = InvoiceFactory.createLateFeeDebitNoteDraft(header(InvoiceType.LATE_FEE), line(FeeType.LATE_FEE, "500.00", "LATE"), ACTOR);

        debitNote.issueInvoice(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE, 7), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);

        assertThat(debitNote.getInvoiceNumber().value()).isEqualTo("ND-00000007");
        assertThat(debitNote.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    void issuingNeedsEveryArgument() {
        Invoice invoice = monthlyDraft("10.00");
        InvoiceNumber number = InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 1);

        assertThatThrownBy(() -> invoice.issueInvoice(null, ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.issueInvoice(number, null, DUE_DATE, SNAPSHOT, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.issueInvoice(number, ISSUE_DATE, null, SNAPSHOT, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.issueInvoice(number, ISSUE_DATE, DUE_DATE, null, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.issueInvoice(number, ISSUE_DATE, DUE_DATE, SNAPSHOT, null)).isInstanceOf(NullPointerException.class);
    }
}
