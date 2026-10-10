package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.DUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-165")
@Tag("D-244")
@Tag("D-192")
class CreditNoteTest {

    private static final LocalDate ISSUE = LocalDate.of(2027, 3, 20);
    private static final LocalDate FACT = LocalDate.of(2027, 3, 19);

    private static Invoice unpaid() {
        return InvoiceTestData.withId(InvoiceTestData.issuedMonthly("80000.00"), 5L);
    }

    private static CreditNote issue(Invoice invoice, CreditNoteReason reason, String justification, String amount) {
        return CreditNote.issueCreditNoteForInvoice(invoice, reason, justification, List.of(new CreditNoteLineRequest(1, ars(amount))),
                CreditNoteNumber.formatCreditNoteNumber(1), ISSUE, FACT, 77L, new StatusChangeActor(9L, null));
    }

    @Test
    void aCreditNoteOnAnUnpaidInvoiceOffsetsTheDebt() {
        CreditNote note = issue(unpaid(), CreditNoteReason.BILLING_ERROR, "Error de carga", "30000.00");

        assertThat(note.getOffsetAmount()).isEqualTo(ars("30000.00"));
        assertThat(note.getFreeCreditAmount()).isEqualTo(ars("0.00"));
        assertThat(note.getTotalAmount()).isEqualTo(ars("30000.00"));
        assertThat(note.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getOffsetPart()).isEqualTo(ars("30000.00"));
            assertThat(line.getFreePart()).isEqualTo(ars("0.00"));
        });
        assertThat(note.offsetsDebt()).isTrue();
    }

    @Test
    void aJustificationOfExactlyAThousandCharactersIsAcceptedAndOneMoreIsNot() {
        assertThat(issue(unpaid(), CreditNoteReason.BILLING_ERROR, "x".repeat(1000), "1.00").getJustification()).hasSize(1000);
        assertThatThrownBy(() -> issue(unpaid(), CreditNoteReason.BILLING_ERROR, "x".repeat(1001), "1.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCreditNoteOnAPaidInvoiceBecomesFreeCredit() {
        Invoice invoice = unpaid();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        CreditNote note = issue(invoice, CreditNoteReason.BILLING_ERROR, "Cobro de más", "10000.00");

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(note.getOffsetAmount()).isEqualTo(ars("0.00"));
        assertThat(note.getFreeCreditAmount()).isEqualTo(ars("10000.00"));
        assertThat(note.offsetsDebt()).isFalse();
    }

    @Test
    void theCreditNoteKeepsTheReferenceToTheInvoiceTheRecipientAndBothDates() {
        CreditNote note = issue(unpaid(), CreditNoteReason.BILLING_ERROR, "  Error  ", "1.00");

        assertThat(note.getInvoiceId()).isEqualTo(5L);
        assertThat(note.getInvoiceNumber()).isEqualTo(unpaid().getInvoiceNumber().value());
        assertThat(note.getRecipientSnapshot()).isEqualTo(SNAPSHOT);
        assertThat(note.getIssueDate()).isEqualTo(ISSUE);
        assertThat(note.getFactDate()).isEqualTo(FACT);
        assertThat(note.getAuthorizationRequestId()).isEqualTo(77L);
        assertThat(note.getIssuedByPersonId()).isEqualTo(9L);
        assertThat(note.getIssuedByProcess()).isNull();
        assertThat(note.getJustification()).isEqualTo("Error");
        assertThat(note.getStudentCurrentAccountId()).isEqualTo(unpaid().getStudentCurrentAccountId());
        assertThat(note.getCreditNoteNumber().value()).isEqualTo("NC-00000001");
    }

    @Test
    void theApplicationIsADebtReductionUnlessItIsACancellationOfTheWholeOutstanding() {
        Invoice invoice = unpaid();

        assertThat(issue(invoice, CreditNoteReason.BILLING_ERROR, "x", "80000.00").toInvoiceCreditNoteApplication(invoice.getAmounts().outstandingAmount()).kind())
                .isEqualTo(CreditNoteApplicationKind.DEBT_REDUCTION);
        assertThat(issue(invoice, CreditNoteReason.CANCELLATION, "x", "80000.00").toInvoiceCreditNoteApplication(invoice.getAmounts().outstandingAmount()).kind())
                .isEqualTo(CreditNoteApplicationKind.CANCELLATION);
        assertThat(issue(invoice, CreditNoteReason.CANCELLATION, "x", "30000.00").toInvoiceCreditNoteApplication(invoice.getAmounts().outstandingAmount()).kind())
                .isEqualTo(CreditNoteApplicationKind.DEBT_REDUCTION);
    }

    @Test
    void anInvalidCreditNoteIsRejectedByTheDomain() {
        Invoice invoice = unpaid();

        assertThatThrownBy(() -> issue(invoice, CreditNoteReason.BILLING_ERROR, " ", "1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(invoice, CreditNoteReason.BILLING_ERROR, null, "1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(invoice, CreditNoteReason.BILLING_ERROR, "x".repeat(1001), "1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditNote.issueCreditNoteForInvoice(invoice, CreditNoteReason.BILLING_ERROR, "x", List.of(),
                CreditNoteNumber.formatCreditNoteNumber(1), ISSUE, FACT, null, ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditNoteLineRequest(0, ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditNoteLineRequest(1, ars("0.00"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditNoteNumbersAreEightDigitsAndBounded() {
        assertThat(CreditNoteNumber.formatCreditNoteNumber(42).value()).isEqualTo("NC-00000042");
        assertThat(CreditNoteNumber.formatCreditNoteNumber(99_999_999L).value()).isEqualTo("NC-99999999");
        assertThatThrownBy(() -> CreditNoteNumber.formatCreditNoteNumber(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditNoteNumber.formatCreditNoteNumber(100_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditNoteNumber("F-00000001")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDestinationFollowsThePaymentStatus() {
        assertThat(CreditNoteDestination.ofInvoiceStatus(InvoicePaymentStatus.PAID)).isEqualTo(CreditNoteDestination.FREE_CREDIT);
        assertThat(CreditNoteDestination.ofInvoiceStatus(InvoicePaymentStatus.PENDING)).isEqualTo(CreditNoteDestination.DEBT_OFFSET);
        assertThat(CreditNoteDestination.ofInvoiceStatus(InvoicePaymentStatus.OVERDUE)).isEqualTo(CreditNoteDestination.DEBT_OFFSET);
    }

    @Test
    void theCommandAsksTheDirectorWithAReasonAndKeepsTheAuthorizationRequestWhenResumed() {
        IssueCreditNoteCommand command = new IssueCreditNoteCommand(5L, CreditNoteReason.BILLING_ERROR, "Error", List.of(), CreditNoteDestination.DEBT_OFFSET, 9L, false, null);

        assertThat(command.authorizationReason()).contains("BILLING_ERROR").contains("Error");
        assertThat(command.authorizationExpiresOn()).isNull();
        assertThat(command.resumedFromAuthorizationRequest(33L).authorizationRequestId()).isEqualTo(33L);
        assertThat(command.resumedFromAuthorizationRequest(33L).invoiceId()).isEqualTo(5L);
    }

    @Test
    void theFactsOfAnInvoiceExposeItsLinesAndPaidState() {
        Invoice invoice = unpaid();
        CreditedInvoiceFacts facts = CreditedInvoiceFacts.of(invoice);

        assertThat(facts.findLine(1)).isPresent();
        assertThat(facts.findLine(2)).isEmpty();
        assertThat(facts.freeCreditedAmount()).isEqualTo(ars("0.00"));
        assertThat(facts.recipientDocumentNumber()).isEqualTo(SNAPSHOT.documentNumber());
    }
}
