package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

class AnnualUpfrontQuoteCalculatorTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 2, 12, 10, 0);
    private static final List<YearMonth> PERIODS = IntStream.range(0, 10).mapToObj(offset -> YearMonth.of(2027, 3).plusMonths(offset)).toList();

    private static Map<Integer, InvoiceDiscountCandidate> tenPercentOnEveryLine() {
        Map<Integer, InvoiceDiscountCandidate> winners = new TreeMap<>();
        for (int line = 1; line <= 10; line++) {
            winners.put(line, new InvoiceDiscountCandidate(DiscountType.ANNUAL_UPFRONT, 4L, null, "Pago anual 10%", InvoiceType.ANNUAL_UPFRONT, ars("80000.00"), ars("8000.00"),
                    null, null, line, 1, null, new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.TEN)), DiscountCandidateOrigin.ANNUAL_UPFRONT_REQUEST, 4L));
        }
        return winners;
    }

    @Test
    @Tag("D-295")
    void tenInstallmentsWithAnAnnualPromotionListTheDiscountPerLineAndTheTotal() {
        AnnualUpfrontQuote quote = AnnualUpfrontQuoteCalculator.calculateQuote(PERIODS, ars("80000.00"), tenPercentOnEveryLine(), DEADLINE);

        assertThat(quote.lines()).hasSize(10);
        assertThat(quote.lines()).allSatisfy(line -> {
            assertThat(line.baseAmount().amount()).isEqualByComparingTo("80000.00");
            assertThat(line.discountAmount().amount()).isEqualByComparingTo("8000.00");
            assertThat(line.finalAmount().amount()).isEqualByComparingTo("72000.00");
        });
        assertThat(quote.lines().get(0).period()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(quote.lines().get(9).period()).isEqualTo(YearMonth.of(2027, 12));
        assertThat(quote.total().amount()).isEqualByComparingTo("720000.00");
        assertThat(quote.discount().definitionId()).isEqualTo(4L);
        assertThat(quote.discount().totalSaving().amount()).isEqualByComparingTo("80000.00");
        assertThat(quote.paymentDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    @Tag("D-440")
    void withoutAnyPromotionTheQuoteHasNoDiscountAndTheFullTotal() {
        AnnualUpfrontQuote quote = AnnualUpfrontQuoteCalculator.calculateQuote(PERIODS, ars("80000.00"), Map.of(), DEADLINE);

        assertThat(quote.discount()).isNull();
        assertThat(quote.total().amount()).isEqualByComparingTo("800000.00");
        assertThat(quote.lines()).allSatisfy(line -> assertThat(line.discountAmount().isZero()).isTrue());
    }
}
