package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.DiscountExceedsLineAmountException;
import com.red_escuela.backend.billing.invoice.exception.IssuedInvoiceCannotBeModifiedException;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-075")
@Tag("D-073")
@Tag("D-097")
class InvoiceDraftDiscountTest {

    @Test
    void aFiftyPercentScholarshipOnInstallmentTwoOfThreeLowersTheTotal() {
        Invoice draft = monthlyDraft("80000.00");

        draft.addDiscountToDraftLine(1, new InvoiceDiscountDraft(DiscountType.SCHOLARSHIP, "Beca 50 %", 7L, ars("80000.00"), ars("40000.00"), 2, 3));

        InvoiceDiscount discount = draft.getLines().get(0).getDiscount();
        assertThat(discount.describeInstallmentProgress()).contains("2 de 3");
        assertThat(discount.getDiscountAmount()).isEqualTo(ars("40000.00"));
        assertThat(discount.getStudentDiscountId()).isEqualTo(7L);
        assertThat(draft.getLines().get(0).getDiscountAmount()).isEqualTo(ars("40000.00"));
        assertThat(draft.getAmounts().total()).isEqualTo(ars("40000.00"));
        assertThat(draft.getAmounts().discountsTotal()).isEqualTo(ars("40000.00"));
    }

    @Test
    void aDiscountWithoutALimitOrWithoutProgressDescribesItselfAccordingly() {
        Invoice draft = monthlyDraft("100.00");
        draft.addDiscountToDraftLine(1, new InvoiceDiscountDraft(DiscountType.PROMOTION, "Promo", null, ars("100.00"), ars("10.00"), 2, null));

        assertThat(draft.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("2");
        assertThat(new InvoiceDiscount(draft.getLines().get(0), discount("100.00", "10.00")).describeInstallmentProgress()).isEmpty();
    }

    @Test
    void aDiscountOneCentOverTheLineIsRejected() {
        Invoice draft = monthlyDraft("80000.00");

        assertThatThrownBy(() -> draft.addDiscountToDraftLine(1, discount("80000.01", "80000.01"))).isInstanceOf(DiscountExceedsLineAmountException.class);
        assertThatThrownBy(() -> draft.addDiscountToDraftLine(1, discount("80000.00", "80000.01"))).isInstanceOf(DiscountExceedsLineAmountException.class);
        assertThat(draft.getAmounts().total()).isEqualTo(ars("80000.00"));
    }

    @Test
    void aDiscountEqualToTheLineIsAccepted() {
        Invoice draft = monthlyDraft("80000.00");

        draft.addDiscountToDraftLine(1, discount("80000.00", "80000.00"));

        assertThat(draft.getAmounts().total()).isEqualTo(ars("0.00"));
    }

    @Test
    void aSecondDiscountOnTheSameLineIsRejected() {
        Invoice draft = monthlyDraft("80000.00");
        draft.addDiscountToDraftLine(1, discount("80000.00", "100.00"));

        assertThatThrownBy(() -> draft.addDiscountToDraftLine(1, discount("80000.00", "100.00"))).isInstanceOf(DiscountExceedsLineAmountException.class);
        assertThat(draft.getAmounts().discountsTotal()).isEqualTo(ars("100.00"));
    }

    @Test
    void aDiscountInAnotherCurrencyIsRejected() {
        Invoice draft = monthlyDraft("80000.00");
        InvoiceDiscountDraft usdAmount = new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "USD", null, ars("80000.00"), usd("10.00"), null, null);
        InvoiceDiscountDraft usdBase = new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "USD", null, usd("80000.00"), ars("10.00"), null, null);

        assertThatThrownBy(() -> draft.addDiscountToDraftLine(1, usdAmount)).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertThatThrownBy(() -> draft.addDiscountToDraftLine(1, usdBase)).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
    }

    @Test
    void aLineInAnotherCurrencyIsRejected() {
        Invoice draft = annualDraft(0);

        assertThatThrownBy(() -> draft.addChargeLineToDraft(new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "USD", java.time.YearMonth.of(2027, 3), 1L, null, null, null, 1L, "K", usd("10.00"))))
                .isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
    }

    @Test
    void aDiscountOnAnUnknownLineIsAProgrammingError() {
        Invoice draft = monthlyDraft("80000.00");

        assertThatThrownBy(() -> draft.addDiscountToDraftLine(2, discount("80000.00", "1.00"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anIssuedInvoiceRejectsNewDiscounts() {
        Invoice issued = issuedMonthly("80000.00");

        assertThatThrownBy(() -> issued.addDiscountToDraftLine(1, discount("80000.00", "1.00"))).isInstanceOf(IssuedInvoiceCannotBeModifiedException.class);
    }

    @Test
    void invalidDiscountDraftsAreRejected() {
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), ars("0.00"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, " ", null, ars("1.00"), ars("1.00"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x".repeat(201), null, ars("1.00"), ars("1.00"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), ars("1.00"), null, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), ars("1.00"), 0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), ars("1.00"), 4, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x".repeat(200), null, ars("1.00"), ars("1.00"), 3, 3)).isNotNull();
        assertThat(new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), ars("1.00"), 1, null)).isNotNull();
        assertThatThrownBy(() -> new InvoiceDiscountDraft(null, "x", null, ars("1.00"), ars("1.00"), null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, null, ars("1.00"), null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceDiscountDraft(DiscountType.INDIVIDUAL, "x", null, ars("1.00"), null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void invalidLineDraftsAreRejected() {
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "K", ars("0.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "K", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "x".repeat(201), java.time.YearMonth.of(2027, 3), null, null, null, null, null, "K", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, null, ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "k".repeat(121), ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), 1L, 2L, null, null, null, "K", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "c", java.time.YearMonth.of(2027, 3), 1L, null, null, null, null, "K", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.ENROLLMENT_FEE, "c", java.time.YearMonth.of(2027, 3), 1L, null, null, null, null, "K", ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(null, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "K", ars("1.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", null, null, null, null, null, null, "K", ars("1.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceLineDraft(FeeType.EXAM_FEE, "c", java.time.YearMonth.of(2027, 3), null, null, null, null, null, "K", null)).isInstanceOf(NullPointerException.class);
        assertThat(new InvoiceLineDraft(FeeType.EXAM_FEE, "x".repeat(200), java.time.YearMonth.of(2027, 3), null, null, null, null, null, "k".repeat(120), ars("0.01"))).isNotNull();
    }
}
