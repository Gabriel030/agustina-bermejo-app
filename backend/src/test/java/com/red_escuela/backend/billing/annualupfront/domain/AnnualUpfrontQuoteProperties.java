package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountCandidateFinder;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCalculator;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingOperationConfiguration;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-099")
@Tag("D-171")
class AnnualUpfrontQuoteProperties {

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());

    @Property(seed = "20271028")
    void theTotalIsTheSumOfTheLinesAndNeverNegative(@ForAll @LongRange(min = 1, max = 99_999_999) long priceInCents, @ForAll @IntRange(min = 1, max = 10000) int percentInHundredths,
                                                    @ForAll @IntRange(min = 1, max = 10) int lineCount) {
        NonNegativeMoney price = NonNegativeMoney.of(BigDecimal.valueOf(priceInCents, 2), Currency.ARS);
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.annualUpfront().availableFrom(LocalDate.of(2027, 1, 1))
                .withPercent(BigDecimal.valueOf(percentInHundredths, 2).toPlainString()).build(), 99L, Instant.parse("2027-01-05T10:00:00Z"));
        ReflectionTestUtils.setField(definition, "id", 1L);
        List<YearMonth> periods = IntStream.range(0, lineCount).mapToObj(offset -> YearMonth.of(2027, 3).plusMonths(offset)).toList();
        List<InvoiceDiscountCandidate> candidates = AnnualUpfrontDiscountCandidateFinder.candidatesOf(List.of(definition), 7L, Currency.ARS, LocalDate.of(2027, 2, 10),
                periods.stream().map(period -> price).toList(), id -> 0L);
        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(InvoiceType.ANNUAL_UPFRONT, candidates);

        AnnualUpfrontQuote quote = AnnualUpfrontQuoteCalculator.calculateQuote(periods, price, winners, LocalDateTime.of(2027, 2, 12, 10, 0));

        Money sum = quote.lines().stream().map(AnnualUpfrontQuoteLine::finalAmount).reduce(Money.zero(Currency.ARS), Money::plus);
        assertThat(quote.total()).isEqualTo(sum);
        assertThat(quote.lines()).allSatisfy(line -> {
            assertThat(line.finalAmount().isNegative()).isFalse();
            assertThat(line.discountAmount().isGreaterThan(line.baseAmount())).isFalse();
            assertThat(line.finalAmount()).isEqualTo(line.baseAmount().minus(line.discountAmount()));
            assertThat(line.discountAmount().amount().scale()).isLessThanOrEqualTo(2);
        });
    }
}
