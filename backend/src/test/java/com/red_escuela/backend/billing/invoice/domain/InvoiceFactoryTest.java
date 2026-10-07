package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.InvoiceLineNotAllowedForInvoiceTypeException;
import com.red_escuela.backend.billing.invoice.exception.InvoiceLinesEnrollmentMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-084")
@Tag("D-131")
@Tag("D-060")
@Tag("D-140")
@Tag("D-009")
class InvoiceFactoryTest {

    private static Invoice createFor(InvoiceType type) {
        InvoiceDraftHeader header = header(type);
        InvoiceLineDraft line = line(feeTypeOf(type), "25000.50", "KEY");
        return switch (type) {
            case MONTHLY_INSTALLMENT -> InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, Optional.empty(), ACTOR);
            case ANNUAL_UPFRONT -> InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, List.of(line), Map.of(), ACTOR);
            case ENROLLMENT_FEE -> InvoiceFactory.createEnrollmentFeeInvoiceDraft(header, line, Optional.empty(), ACTOR);
            case EXAM_FEE -> InvoiceFactory.createExamFeeInvoiceDraft(header, line, ACTOR);
            case RETAKEN_SUBJECT_FEE -> InvoiceFactory.createRetakenSubjectFeeInvoiceDraft(header, line, ACTOR);
            case DEGREE_ISSUANCE_FEE -> InvoiceFactory.createDegreeIssuanceFeeInvoiceDraft(header, line, ACTOR);
            case ADMINISTRATIVE_FEE -> InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            case LATE_FEE -> InvoiceFactory.createLateFeeDebitNoteDraft(header, line, ACTOR);
        };
    }

    @ParameterizedTest
    @EnumSource(InvoiceType.class)
    void everyInvoiceTypeHasItsFactoryMethodAndProducesADraftWithTheRightCharge(InvoiceType type) {
        Invoice draft = createFor(type);

        assertThat(draft.getInvoiceType()).isEqualTo(type);
        assertThat(draft.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
        assertThat(draft.getPaymentStatus()).isNull();
        assertThat(draft.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getFeeType()).isEqualTo(feeTypeOf(type));
            assertThat(line.getLineNumber()).isEqualTo(1);
        });
        assertThat(draft.getAmounts().total()).isEqualTo(ars("25000.50"));
        assertThat(draft.getDocumentType()).isEqualTo(type == InvoiceType.LATE_FEE ? InvoiceDocumentType.DEBIT_NOTE : InvoiceDocumentType.INVOICE);
    }

    @ParameterizedTest
    @EnumSource(InvoiceType.class)
    void aFactoryMethodRejectsAHeaderOfAnotherInvoiceType(InvoiceType type) {
        InvoiceType other = type == InvoiceType.EXAM_FEE ? InvoiceType.MONTHLY_INSTALLMENT : InvoiceType.EXAM_FEE;
        InvoiceDraftHeader wrongHeader = header(other);
        InvoiceLineDraft line = line(feeTypeOf(type), "100.00", "K");

        assertThatThrownBy(() -> { switch (type) {
            case MONTHLY_INSTALLMENT -> InvoiceFactory.createMonthlyInstallmentInvoiceDraft(wrongHeader, line, Optional.empty(), ACTOR);
            case ANNUAL_UPFRONT -> InvoiceFactory.createAnnualUpfrontInvoiceDraft(wrongHeader, List.of(line), Map.of(), ACTOR);
            case ENROLLMENT_FEE -> InvoiceFactory.createEnrollmentFeeInvoiceDraft(wrongHeader, line, Optional.empty(), ACTOR);
            case EXAM_FEE -> InvoiceFactory.createExamFeeInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), line, ACTOR);
            case RETAKEN_SUBJECT_FEE -> InvoiceFactory.createRetakenSubjectFeeInvoiceDraft(wrongHeader, line, ACTOR);
            case DEGREE_ISSUANCE_FEE -> InvoiceFactory.createDegreeIssuanceFeeInvoiceDraft(wrongHeader, line, ACTOR);
            case ADMINISTRATIVE_FEE -> InvoiceFactory.createAdministrativeFeeInvoiceDraft(wrongHeader, line, ACTOR);
            case LATE_FEE -> InvoiceFactory.createLateFeeDebitNoteDraft(wrongHeader, line, ACTOR);
        } }).isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
    }

    @Test
    void anAnnualUpfrontInvoiceAcceptsTenMonthlyLinesAndRejectsAnEleventh() {
        Invoice annual = annualDraft(10);

        assertThat(annual.getLines()).hasSize(10);
        assertThat(annual.getAmounts().total()).isEqualTo(ars("10000.00"));
        assertThatThrownBy(() -> annual.addChargeLineToDraft(monthlyLine("1000.00", "ELEVENTH")))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
        assertThat(annual.getLines()).hasSize(10);
    }

    @Test
    void anAnnualUpfrontInvoiceCanCarryDiscountsByLineNumber() {
        List<InvoiceLineDraft> lines = new ArrayList<>(List.of(monthlyLine("1000.00", "A"), monthlyLine("1000.00", "B")));

        Invoice annual = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), lines,
                Map.of(2, discount("1000.00", "100.00")), ACTOR);

        assertThat(annual.getAmounts().total()).isEqualTo(ars("1900.00"));
        assertThat(annual.getLines().get(0).getDiscount()).isNull();
        assertThat(annual.getLines().get(1).getDiscount()).isNotNull();
    }

    @Test
    void aForeignFeeTypeIsRejected() {
        Invoice monthly = monthlyDraft("100.00");
        Invoice exam = InvoiceFactory.createExamFeeInvoiceDraft(header(InvoiceType.EXAM_FEE), line(FeeType.EXAM_FEE, "10.00", "E"), ACTOR);

        assertThatThrownBy(() -> exam.addChargeLineToDraft(line(FeeType.LATE_FEE, "10.00", "L")))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
        assertThat(monthly.getLines()).hasSize(1);
    }

    @Test
    void aSecondLineInAMonthlyInstallmentIsRejected() {
        Invoice monthly = monthlyDraft("100.00");

        assertThatThrownBy(() -> monthly.addChargeLineToDraft(monthlyLine("50.00", "SECOND")))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
    }

    @Test
    void linesOfDifferentEnrollmentsAreRejected() {
        Invoice annual = annualDraft(1);
        InvoiceLineDraft otherProgram = new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Otra", YearMonth.of(2027, 4), 999L, null, null, null, 5L, "OTHER", ars("10.00"));
        InvoiceLineDraft course = new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Curso", YearMonth.of(2027, 4), null, 123L, null, null, 5L, "COURSE", ars("10.00"));

        assertThatThrownBy(() -> annual.addChargeLineToDraft(otherProgram)).isInstanceOf(InvoiceLinesEnrollmentMismatchException.class);
        assertThatThrownBy(() -> annual.addChargeLineToDraft(course)).isInstanceOf(InvoiceLinesEnrollmentMismatchException.class);
        assertThat(annual.getLines()).hasSize(1);
    }

    @Test
    void aDebitNoteNeedsItsOriginInvoiceAndAnInvoiceCannotHaveOne() {
        InvoiceDraftHeader noOrigin = new InvoiceDraftHeader(1L, Currency.ARS, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceIssuingReason.LATE_FEE, null);
        InvoiceDraftHeader invoiceWithOrigin = new InvoiceDraftHeader(1L, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.EXAM_FEE, InvoiceIssuingReason.AD_HOC, 5L);
        InvoiceDraftHeader debitNoteOfOtherType = new InvoiceDraftHeader(1L, Currency.ARS, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.EXAM_FEE, InvoiceIssuingReason.AD_HOC, 5L);
        InvoiceDraftHeader lateFeeAsInvoice = new InvoiceDraftHeader(1L, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.LATE_FEE, InvoiceIssuingReason.LATE_FEE, null);

        assertThatThrownBy(() -> InvoiceFactory.createLateFeeDebitNoteDraft(noOrigin, line(FeeType.LATE_FEE, "10.00", "L"), ACTOR))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
        assertThatThrownBy(() -> InvoiceFactory.createExamFeeInvoiceDraft(invoiceWithOrigin, line(FeeType.EXAM_FEE, "10.00", "E"), ACTOR))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
        assertThatThrownBy(() -> InvoiceFactory.createExamFeeInvoiceDraft(debitNoteOfOtherType, line(FeeType.EXAM_FEE, "10.00", "E"), ACTOR))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
        assertThatThrownBy(() -> InvoiceFactory.createLateFeeDebitNoteDraft(lateFeeAsInvoice, line(FeeType.LATE_FEE, "10.00", "L"), ACTOR))
                .isInstanceOf(InvoiceLineNotAllowedForInvoiceTypeException.class);
    }

    @Test
    void aDebitNoteKeepsItsOriginInvoice() {
        Invoice debitNote = createFor(InvoiceType.LATE_FEE);

        assertThat(debitNote.getOriginInvoiceId()).isEqualTo(99L);
    }

    @Test
    void aMonthlyInstallmentDraftCarriesAnOptionalDiscount() {
        Invoice draft = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT),
                monthlyLine("80000.00", "K"), Optional.of(discount("80000.00", "40000.00")), ACTOR);

        assertThat(draft.getAmounts().total()).isEqualTo(ars("40000.00"));
    }
}
