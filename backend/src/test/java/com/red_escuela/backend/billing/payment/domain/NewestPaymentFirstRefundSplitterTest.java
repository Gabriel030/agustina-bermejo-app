package com.red_escuela.backend.billing.payment.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-209")
@Tag("D-159")
class NewestPaymentFirstRefundSplitterTest {

    private static final RefundDestination OTHER_DESTINATION = new RefundDestination("alias.otro", "Otra Persona");

    private static RefundablePayment payment(long id, String paidOn, PaymentMethodType method, String refundable) {
        return new RefundablePayment(id, LocalDate.parse(paidOn), method, ars(refundable), RefundFixtures.DESTINATION);
    }

    private static List<String> summary(List<RefundPart> parts) {
        return parts.stream().map(part -> part.paymentId() + ":" + part.methodType() + ":" + part.amount().value().amount().toPlainString()).toList();
    }

    @Test
    void aSinglePaymentTakesTheWholeAmount() {
        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("300.00").value(), List.of(payment(1, "2027-01-10", PaymentMethodType.CASH, "500.00")));

        assertThat(summary(parts)).containsExactly("1:CASH:300.00");
    }

    @Test
    void twoPaymentsAreSplitFromTheNewestToTheOldestEachByItsOwnMethod() {
        List<RefundablePayment> payments = List.of(payment(1, "2027-01-10", PaymentMethodType.BANK_TRANSFER, "200000.00"),
                payment(2, "2027-02-10", PaymentMethodType.CASH, "250000.00"));

        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("300000.00").value(), payments);

        assertThat(summary(parts)).containsExactly("2:CASH:250000.00", "1:BANK_TRANSFER:50000.00");
    }

    @Test
    void thePartsAreOrderedByPaidOnDescendingAndNotByTheOrderOfTheInput() {
        List<RefundablePayment> payments = List.of(payment(3, "2027-03-10", PaymentMethodType.CASH, "100.00"), payment(1, "2027-01-10", PaymentMethodType.CASH, "100.00"),
                payment(2, "2027-02-10", PaymentMethodType.CASH, "100.00"));

        assertThat(summary(NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("250.00").value(), payments))).containsExactly("3:CASH:100.00", "2:CASH:100.00", "1:CASH:50.00");
    }

    @Test
    void paymentsOfTheSameDayAreOrderedByIdDescending() {
        List<RefundablePayment> payments = List.of(payment(4, "2027-03-10", PaymentMethodType.CASH, "100.00"), payment(9, "2027-03-10", PaymentMethodType.CASH, "100.00"));

        assertThat(summary(NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("150.00").value(), payments))).containsExactly("9:CASH:100.00", "4:CASH:50.00");
    }

    @Test
    void theRefundableAmountOfEachPaymentIsRespectedAndAPaymentAlreadyFullyReturnedIsSkipped() {
        List<RefundablePayment> payments = List.of(payment(1, "2027-01-10", PaymentMethodType.CASH, "100.00"), payment(2, "2027-02-10", PaymentMethodType.CASH, "0.00"),
                payment(3, "2027-03-10", PaymentMethodType.CASH, "30.00"));

        assertThat(summary(NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("80.00").value(), payments))).containsExactly("3:CASH:30.00", "1:CASH:50.00");
    }

    @Test
    void anExactAmountEmptiesThePaymentAndCreatesNoExtraPart() {
        List<RefundablePayment> payments = List.of(payment(1, "2027-01-10", PaymentMethodType.CASH, "100.00"), payment(2, "2027-02-10", PaymentMethodType.CASH, "100.00"));

        assertThat(summary(NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("100.00").value(), payments))).containsExactly("2:CASH:100.00");
    }

    @Test
    void thePartsAlwaysAddUpToTheRequestedAmountAndKeepTheDestinationOfTheirPayment() {
        List<RefundablePayment> payments = List.of(payment(1, "2027-01-10", PaymentMethodType.CASH, "33.33"),
                new RefundablePayment(2L, LocalDate.parse("2027-02-10"), PaymentMethodType.CASH, ars("66.67"), OTHER_DESTINATION));

        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("100.00").value(), payments);

        assertThat(parts.stream().map(part -> part.amount().value().amount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(parts.get(0).destination()).isEqualTo(OTHER_DESTINATION);
        assertThat(parts.get(1).destination()).isEqualTo(RefundFixtures.DESTINATION);
    }

    @Test
    void anAmountAboveWhatThePaymentsCanReturnIsRejected() {
        assertThatThrownBy(() -> NewestPaymentFirstRefundSplitter.splitAmountAcross(ars("100.01").value(), List.of(payment(1, "2027-01-10", PaymentMethodType.CASH, "100.00"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
