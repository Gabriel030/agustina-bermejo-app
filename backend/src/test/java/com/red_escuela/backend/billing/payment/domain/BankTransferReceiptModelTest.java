package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-081")
@Tag("D-153")
class BankTransferReceiptModelTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static PlannedCheckoutItem planned(int position, String outstanding, boolean protectedItem) {
        return new PlannedCheckoutItem(11L, position, ars(outstanding), true, protectedItem);
    }

    @Test
    void aCheckoutItemKeepsWhatWasPlannedForIt() {
        PaymentCheckoutItem item = PaymentCheckoutItem.of(5L, 6L, planned(2, "80000.00", true));

        assertThat(item.getPaymentId()).isEqualTo(5L);
        assertThat(item.getStudentCurrentAccountId()).isEqualTo(6L);
        assertThat(item.getInvoiceId()).isEqualTo(11L);
        assertThat(item.getPosition()).isEqualTo(2);
        assertThat(item.getOutstandingAtSelection()).isEqualTo(NonNegativeMoney.of(ars("80000.00")));
        assertThat(item.isMarkedUnderReview()).isTrue();
        assertThat(PaymentCheckoutItem.of(5L, 6L, planned(1, "1.00", false)).isMarkedUnderReview()).isFalse();
    }

    @Test
    void aCheckoutItemStartsAtPositionOneAndNeedsAPositiveOutstandingAmount() {
        assertThat(PaymentCheckoutItem.of(5L, 6L, planned(1, "0.01", false)).getPosition()).isEqualTo(1);
        assertThatThrownBy(() -> PaymentCheckoutItem.of(5L, 6L, planned(0, "10.00", false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentCheckoutItem.of(5L, 6L, planned(1, "0.00", false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCheckoutItemNeedsItsPaymentAccountAndInvoice() {
        assertThatThrownBy(() -> PaymentCheckoutItem.of(null, 6L, planned(1, "10.00", false))).isInstanceOf(NullPointerException.class).hasMessageContaining("paymentId");
        assertThatThrownBy(() -> PaymentCheckoutItem.of(5L, null, planned(1, "10.00", false))).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("studentCurrentAccountId");
        assertThatThrownBy(() -> PaymentCheckoutItem.of(5L, 6L, new PlannedCheckoutItem(null, 1, ars("10.00"), true, false))).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("invoiceId");
    }

    @Test
    void aSubmittedReceiptStartsPendingReviewLinkedToItsPaymentWithItsDeclaredDetails() {
        BankTransferPaymentDetails details = new BankTransferPaymentDetails("Banco", "0170099220000067797370", "OP-1", "Ana Pérez", LocalDate.of(2027, 3, 9));
        NonNegativeMoney declared = NonNegativeMoney.of(ars("80000.00"));
        LocalDateTime before = ApplicationTime.now();

        BankTransferReceiptRecord receipt = BankTransferReceiptRecord.submittedFor(11L, "students/1/comprobante.pdf", 99L, 5L, declared, details);

        LocalDateTime after = ApplicationTime.now();
        assertThat(receipt.getInvoiceId()).isEqualTo(11L);
        assertThat(receipt.getFilePath()).isEqualTo("students/1/comprobante.pdf");
        assertThat(receipt.getUploadedByPersonId()).isEqualTo(99L);
        assertThat(receipt.getPaymentId()).isEqualTo(5L);
        assertThat(receipt.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(receipt.getDeclaredAmount()).isEqualTo(declared);
        assertThat(receipt.getTransferDate()).isEqualTo(LocalDate.of(2027, 3, 9));
        assertThat(receipt.getOperationNumber()).isEqualTo("OP-1");
        assertThat(receipt.getOriginAccount()).isEqualTo("0170099220000067797370");
        assertThat(receipt.getHolderName()).isEqualTo("Ana Pérez");
        assertThat(receipt.getUploadedAt()).isBetween(before, after);
    }

    @Test
    void aSubmittedReceiptNeedsItsInvoiceFilePaymentAndAmount() {
        BankTransferPaymentDetails details = new BankTransferPaymentDetails(null, "CBU", "OP", "Ana", LocalDate.of(2027, 3, 9));
        NonNegativeMoney declared = NonNegativeMoney.of(ars("1.00"));

        assertThatThrownBy(() -> BankTransferReceiptRecord.submittedFor(null, "f", 1L, 5L, declared, details)).hasMessageContaining("invoiceId");
        assertThatThrownBy(() -> BankTransferReceiptRecord.submittedFor(1L, null, 1L, 5L, declared, details)).hasMessageContaining("filePath");
        assertThatThrownBy(() -> BankTransferReceiptRecord.submittedFor(1L, "f", 1L, null, declared, details)).hasMessageContaining("paymentId");
        assertThatThrownBy(() -> BankTransferReceiptRecord.submittedFor(1L, "f", 1L, 5L, null, details)).hasMessageContaining("declaredAmount");
        assertThat(BankTransferReceiptRecord.submittedFor(1L, "f", null, 5L, declared, details).getUploadedByPersonId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"PENDING,true,true,false", "OVERDUE,true,true,false", "PAYMENT_UNDER_REVIEW,true,false,true", "PAID,true,false,false", "CANCELLED,true,false,false",
            "PENDING,false,false,false", "PAYMENT_UNDER_REVIEW,false,false,false"})
    void anInvoiceFactKnowsWhetherItIsOpenToAReceiptOrUnderReview(InvoicePaymentStatus status, boolean issued, boolean open, boolean underReview) {
        ReceiptInvoiceFact fact = new ReceiptInvoiceFact(1L, 2L, Currency.ARS, issued, status, LocalDate.of(2027, 3, 10), ars("10.00"));

        assertThat(fact.isOpenToReceipt()).isEqualTo(open);
        assertThat(fact.isUnderReview()).isEqualTo(underReview);
    }

    @Test
    void anInvoiceFactNeedsItsIdentityAndOrdersByDueDateThenId() {
        assertThatThrownBy(() -> new ReceiptInvoiceFact(null, 2L, Currency.ARS, true, InvoicePaymentStatus.PENDING, null, null)).hasMessageContaining("invoiceId");
        assertThatThrownBy(() -> new ReceiptInvoiceFact(1L, null, Currency.ARS, true, InvoicePaymentStatus.PENDING, null, null)).hasMessageContaining("studentCurrentAccountId");
        assertThatThrownBy(() -> new ReceiptInvoiceFact(1L, 2L, null, true, InvoicePaymentStatus.PENDING, null, null)).hasMessageContaining("currency");
        ReceiptInvoiceFact noDueDate = new ReceiptInvoiceFact(1L, 2L, Currency.ARS, true, InvoicePaymentStatus.PENDING, null, ars("10.00"));
        ReceiptInvoiceFact early = new ReceiptInvoiceFact(9L, 2L, Currency.ARS, true, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 3, 10), ars("10.00"));
        ReceiptInvoiceFact sameDayHigherId = new ReceiptInvoiceFact(10L, 2L, Currency.ARS, true, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 3, 10), ars("10.00"));

        List<ReceiptInvoiceFact> sorted = new ArrayList<>(List.of(noDueDate, sameDayHigherId, early));
        sorted.sort(ReceiptInvoiceFact.OLDEST_DUE_FIRST);

        assertThat(sorted).containsExactly(early, sameDayHigherId, noDueDate);
    }

    @ParameterizedTest
    @CsvSource({"0.00,true,false", "-0.01,true,false", "0.01,false,false", "9999999999.99,false,false", "10000000000.00,false,true"})
    void theAmountLimitsFollowTheColumnOfTheDeclaredAmount(String amount, boolean notPositive, boolean exceeds) {
        Money money = ars(amount);

        assertThat(BankTransferReceiptLimits.isNotPositive(money)).isEqualTo(notPositive);
        assertThat(BankTransferReceiptLimits.exceedsStorableAmount(money)).isEqualTo(exceeds);
    }

    @Test
    void theSubmissionResultKeepsItsItemsAsACopy() {
        List<BankTransferReceiptSubmissionResult.Item> items = new ArrayList<>();
        items.add(new BankTransferReceiptSubmissionResult.Item(1L, 1, NonNegativeMoney.of(ars("10.00")), true));

        BankTransferReceiptSubmissionResult result = new BankTransferReceiptSubmissionResult(1L, 2L, 3L, NonNegativeMoney.of(ars("10.00")), LocalDate.of(2027, 3, 9), items);
        items.clear();

        assertThat(result.items()).hasSize(1);
        assertThatThrownBy(() -> result.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thePlannerNeedsTheDeclaredAmount() {
        assertThatThrownBy(() -> BankTransferReceiptSelectionPlanner.planItems(List.of(), null, LocalDate.of(2027, 3, 9), LocalDate.of(2027, 3, 9), facts -> true))
                .hasMessageContaining("declaredAmount");
    }
}
