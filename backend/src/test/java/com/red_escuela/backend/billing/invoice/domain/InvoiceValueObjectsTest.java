package com.red_escuela.backend.billing.invoice.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-051")
class InvoiceValueObjectsTest {

    @Test
    void formatsInvoiceAndDebitNoteNumbersWithEightDigits() {
        assertThat(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 1).value()).isEqualTo("F-00000001");
        assertThat(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE, 1).value()).isEqualTo("ND-00000001");
        assertThat(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 99_999_999L).value()).isEqualTo("F-99999999");
    }

    @Test
    void aSequenceOutsideTheEightDigitsIsRejected() {
        assertThatThrownBy(() -> InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 100_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceNumber.formatInvoiceNumber(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "F-1", "X-00000001", "F-0000000A", "F-000000012", "ND00000001", "f-00000001"})
    void malformedNumbersAreRejected(String value) {
        assertThatThrownBy(() -> new InvoiceNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullNumberIsRejected() {
        assertThatThrownBy(() -> new InvoiceNumber(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNumberBelongsOnlyToItsDocumentType() {
        InvoiceNumber invoice = InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 5);
        InvoiceNumber debitNote = InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE, 5);

        assertThat(invoice.belongsTo(InvoiceDocumentType.INVOICE)).isTrue();
        assertThat(invoice.belongsTo(InvoiceDocumentType.DEBIT_NOTE)).isFalse();
        assertThat(debitNote.belongsTo(InvoiceDocumentType.DEBIT_NOTE)).isTrue();
        assertThat(debitNote.belongsTo(InvoiceDocumentType.INVOICE)).isFalse();
    }

    @Test
    void aStudentSnapshotNeedsNameAndDocument() {
        assertThat(new StudentSnapshot("Ana", "1", null).address()).isNull();
        assertThatThrownBy(() -> new StudentSnapshot("", "1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentSnapshot(null, "1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentSnapshot("Ana", " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentSnapshot("Ana", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDraftHeaderNeedsEveryMandatoryField() {
        var currency = com.red_escuela.backend.shared.domain.Currency.ARS;
        assertThatThrownBy(() -> new InvoiceDraftHeader(null, currency, InvoiceDocumentType.INVOICE, InvoiceType.EXAM_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDraftHeader(1L, null, InvoiceDocumentType.INVOICE, InvoiceType.EXAM_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDraftHeader(1L, currency, null, InvoiceType.EXAM_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDraftHeader(1L, currency, InvoiceDocumentType.INVOICE, null, InvoiceIssuingReason.AD_HOC_CHARGE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDraftHeader(1L, currency, InvoiceDocumentType.INVOICE, InvoiceType.EXAM_FEE, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aLineExposesItsNetAndCreditableAmounts() {
        Invoice draft = InvoiceTestData.monthlyDraft("100.00");
        draft.addDiscountToDraftLine(1, InvoiceTestData.discount("100.00", "30.00"));
        InvoiceLine line = draft.getLines().get(0);

        assertThat(line.calculateNetAmount()).isEqualTo(InvoiceTestData.ars("70.00"));
        assertThat(line.calculateCreditableAmount()).isEqualTo(InvoiceTestData.ars("70.00"));
        assertThat(line.getConcept()).startsWith("Concepto");
        assertThat(line.getPeriod()).isEqualTo(java.time.YearMonth.of(2027, 3));
        assertThat(line.getProgramEnrollmentId()).isEqualTo(123L);
        assertThat(line.getPriceRevisionId()).isEqualTo(5L);
        assertThat(line.getInvoice()).isSameAs(draft);
        assertThat(line.getCurrency()).isEqualTo(com.red_escuela.backend.shared.domain.Currency.ARS);
        assertThat(line.getChargeKey()).isEqualTo("PE:123:MONTHLY_INSTALLMENT:2027-03");
        assertThat(line.getDiscount().getInvoiceLine()).isSameAs(line);
        assertThat(line.getDiscount().getBaseAmount()).isEqualTo(InvoiceTestData.ars("100.00"));
        assertThat(line.getDiscount().getDiscountType()).isEqualTo(DiscountType.SCHOLARSHIP);
        assertThat(line.getDiscount().getConcept()).isEqualTo("Beca");
        assertThat(line.getDiscount().getCurrency()).isEqualTo(com.red_escuela.backend.shared.domain.Currency.ARS);
    }
}
