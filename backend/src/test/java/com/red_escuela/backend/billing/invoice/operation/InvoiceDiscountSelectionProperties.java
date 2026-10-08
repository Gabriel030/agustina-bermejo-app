package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-075")
@Tag("D-171")
@Tag("D-099")
@Tag("D-073")
class InvoiceDiscountSelectionProperties {

    private static final String SEED = "20271007";
    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(
            new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());

    record Spec(long baseCents, List<long[]> offers) {
    }

    @Provide
    Arbitrary<Spec> specs() {
        return Arbitraries.longs().between(1, 50_000_000L).flatMap(base ->
                Arbitraries.longs().between(1, base).list().ofMinSize(0).ofMaxSize(6)
                        .map(amounts -> new Spec(base, amounts.stream().map(a -> new long[]{a}).toList())));
    }

    @Property(tries = 1000, seed = SEED)
    void theWinnerIsOneCandidateNeverAboveTheBaseAndTheMaximumOffered(@ForAll("specs") Spec spec) {
        DiscountType[] types = DiscountType.values();
        List<InvoiceDiscountCandidate> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < spec.offers().size(); index++) {
            candidates.add(new InvoiceDiscountCandidate(types[index % 3], index + 1L, index + 1L, "d" + index, index % 2 == 0 ? InvoiceType.MONTHLY_INSTALLMENT : InvoiceType.ENROLLMENT_FEE,
                    IssuingTestData.ars(BigDecimal.valueOf(spec.baseCents(), 2).toPlainString()),
                    IssuingTestData.ars(BigDecimal.valueOf(spec.offers().get(index)[0], 2).toPlainString()), null, null));
        }

        var winner = calculator.selectBestDiscountForInvoice(InvoiceType.MONTHLY_INSTALLMENT, candidates);
        List<InvoiceDiscountCandidate> sameType = candidates.stream().filter(c -> c.applicableInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT).toList();

        assertThat(winner.isPresent()).isEqualTo(!sameType.isEmpty());
        winner.ifPresent(chosen -> assertThat(chosen.applicableInvoiceType()).isEqualTo(InvoiceType.MONTHLY_INSTALLMENT));
        winner.ifPresent(chosen -> {
            assertThat(chosen.discountAmount().value().amount()).isLessThanOrEqualTo(chosen.baseAmount().value().amount());
            assertThat(sameType.stream().allMatch(other -> other.discountAmount().value().amount().compareTo(chosen.discountAmount().value().amount()) <= 0)).isTrue();
            assertThat(chosen.baseAmount().value().amount().subtract(chosen.discountAmount().value().amount()).signum()).isGreaterThanOrEqualTo(0);
        });
    }
}
