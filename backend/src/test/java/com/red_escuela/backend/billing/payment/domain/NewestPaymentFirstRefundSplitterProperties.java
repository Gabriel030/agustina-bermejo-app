package com.red_escuela.backend.billing.payment.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-209")
@Tag("D-159")
class NewestPaymentFirstRefundSplitterProperties {

    record Row(int dayOffset, BigDecimal refundable, PaymentMethodType method) {
    }

    record Scenario(List<RefundablePayment> payments, BigDecimal requested) {
    }

    @Provide
    Arbitrary<Scenario> scenariosCoveredByThePayments() {
        return paymentsList().flatMap(payments -> {
            BigDecimal total = totalOf(payments);
            if (total.signum() == 0) {
                return Arbitraries.just(new Scenario(payments, BigDecimal.ZERO));
            }
            return Arbitraries.bigDecimals().between(BigDecimal.ZERO, total).ofScale(2).map(requested -> new Scenario(payments, requested));
        });
    }

    @Provide
    Arbitrary<Scenario> scenariosAboveThePayments() {
        return paymentsList().flatMap(payments -> Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("1000.00")).ofScale(2)
                .map(excess -> new Scenario(payments, totalOf(payments).add(excess))));
    }

    private Arbitrary<List<RefundablePayment>> paymentsList() {
        Arbitrary<Integer> day = Arbitraries.integers().between(0, 6);
        Arbitrary<BigDecimal> refundable = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("500000.00")).ofScale(2);
        Arbitrary<PaymentMethodType> method = Arbitraries.of(PaymentMethodType.CASH, PaymentMethodType.BANK_TRANSFER);
        return Combinators.combine(day, refundable, method).as(Row::new).list().ofMinSize(1).ofMaxSize(8).map(rows -> {
            List<RefundablePayment> payments = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                payments.add(new RefundablePayment((long) index + 1, LocalDate.of(2027, 3, 1).plusDays(row.dayOffset()), row.method(),
                        ars(row.refundable().toPlainString()), RefundFixtures.DESTINATION));
            }
            return payments;
        });
    }

    private static BigDecimal totalOf(List<RefundablePayment> payments) {
        return payments.stream().map(payment -> payment.refundable().value().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static RefundablePayment paymentOf(List<RefundablePayment> payments, Long paymentId) {
        return payments.stream().filter(payment -> payment.paymentId().equals(paymentId)).findFirst().orElseThrow();
    }

    @Property(seed = "20261028")
    void thePartsAddUpToTheRequestedAmountAndNeverExceedWhatEachPaymentCanStillReturn(@ForAll("scenariosCoveredByThePayments") Scenario scenario) {
        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(ars(scenario.requested().toPlainString()).value(), scenario.payments());

        BigDecimal sum = parts.stream().map(part -> part.amount().value().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(scenario.requested());
        Map<Long, BigDecimal> perPayment = parts.stream().collect(Collectors.groupingBy(RefundPart::paymentId,
                Collectors.reducing(BigDecimal.ZERO, part -> part.amount().value().amount(), BigDecimal::add)));
        perPayment.forEach((paymentId, taken) -> assertThat(taken).isLessThanOrEqualTo(paymentOf(scenario.payments(), paymentId).refundable().value().amount()));
        assertThat(parts).allSatisfy(part -> assertThat(part.amount().value().amount()).isPositive());
        assertThat(new HashSet<>(parts.stream().map(RefundPart::paymentId).toList())).hasSameSizeAs(parts);
    }

    @Property(seed = "20261029")
    void anOlderPaymentOnlyContributesWhenEveryNewerOneWasReturnedInFull(@ForAll("scenariosCoveredByThePayments") Scenario scenario) {
        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(ars(scenario.requested().toPlainString()).value(), scenario.payments());

        for (int index = 0; index < parts.size() - 1; index++) {
            RefundPart part = parts.get(index);
            assertThat(part.amount().value().amount()).isEqualByComparingTo(paymentOf(scenario.payments(), part.paymentId()).refundable().value().amount());
        }
        for (int index = 1; index < parts.size(); index++) {
            RefundablePayment newer = paymentOf(scenario.payments(), parts.get(index - 1).paymentId());
            RefundablePayment current = paymentOf(scenario.payments(), parts.get(index).paymentId());
            assertThat(current.paidOn()).isBeforeOrEqualTo(newer.paidOn());
        }
    }

    @Property(seed = "20261030")
    void aRequestAboveWhatThePaymentsCanReturnIsRejectedInsteadOfRefundingMoreThanWasCollected(@ForAll("scenariosAboveThePayments") Scenario scenario) {
        assertThatThrownBy(() -> NewestPaymentFirstRefundSplitter.splitAmountAcross(ars(scenario.requested().toPlainString()).value(), scenario.payments()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
