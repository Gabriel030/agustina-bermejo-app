package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-099")
@Tag("D-075")
@Tag("D-136")
@Tag("D-171")
@Tag("D-174")
class InvoiceAmountsProperties {

    private static final int TRIES = 1000;
    private static final String SEED = "20271003";

    record LineSpec(long cents, int discountPercent) {
    }

    @Provide
    Arbitrary<List<LineSpec>> lineSpecs() {
        return Arbitraries.longs().between(1, 50_000_000L)
                .flatMap(cents -> Arbitraries.integers().between(0, 100).map(percent -> new LineSpec(cents, percent)))
                .list().ofMinSize(1).ofMaxSize(10);
    }

    private static NonNegativeMoney money(long cents) {
        return NonNegativeMoney.of(BigDecimal.valueOf(cents, 2), Currency.ARS);
    }

    @Property(tries = TRIES, seed = SEED)
    void theTotalIsTheSumOfLinesMinusTheSumOfDiscountsAndNeverNegative(@ForAll("lineSpecs") List<LineSpec> specs) {
        Invoice invoice = InvoiceTestData.annualDraft(0);
        long linesCents = 0;
        long discountCents = 0;
        int index = 1;
        for (LineSpec spec : specs) {
            invoice.addChargeLineToDraft(InvoiceTestData.monthlyLine(money(spec.cents()).value().amount().toPlainString(), "KEY" + index));
            long discount = Math.min(spec.cents(), Math.round(spec.cents() * spec.discountPercent() / 100.0));
            if (discount > 0) {
                invoice.addDiscountToDraftLine(index, new InvoiceDiscountDraft(DiscountType.MANUAL, "Manual", null, money(spec.cents()), money(discount), null, null));
            }
            linesCents += spec.cents();
            discountCents += discount;
            index++;
        }

        InvoiceAmounts amounts = invoice.getAmounts();

        assertThat(amounts.linesTotal()).isEqualTo(money(linesCents));
        assertThat(amounts.discountsTotal()).isEqualTo(money(discountCents));
        assertThat(amounts.total()).isEqualTo(money(linesCents - discountCents));
        assertThat(amounts.total().value().isNegative()).isFalse();
    }

    @Property(tries = TRIES, seed = SEED)
    void noValidSequenceOfAmountOperationsLeavesPaidAndOutstandingTogetherOrANegativeAmount(
            @ForAll("lineSpecs") List<LineSpec> specs, @ForAll List<Boolean> operations) {
        long total = specs.get(0).cents();
        InvoiceAmounts amounts = new InvoiceAmounts(money(total), money(0), money(total), money(0), money(0), money(total));
        for (boolean pay : new ArrayList<>(operations)) {
            try {
                if (pay) {
                    amounts = amounts.hasPaidAmount() ? amounts.withPaymentAllocationReversed() : amounts.withFullPaymentAllocated(amounts.outstandingAmount());
                } else if (!amounts.hasPaidAmount() && amounts.hasOutstandingAmount()) {
                    amounts = amounts.withCreditNoteOffset(money(1));
                }
            } catch (RuntimeException expected) {
                assertThat(Optional.of(expected)).isPresent();
            }
            assertThat(!amounts.hasPaidAmount() || !amounts.hasOutstandingAmount()).isTrue();
            assertThat(amounts.outstandingAmount().value().isNegative()).isFalse();
            assertThat(amounts.total().value().minus(amounts.paidAmount().value()).minus(amounts.creditedAmount().value())).isEqualTo(amounts.outstandingAmount().value());
        }
    }
}
