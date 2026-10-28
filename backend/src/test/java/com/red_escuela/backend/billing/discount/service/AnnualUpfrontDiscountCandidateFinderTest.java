package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCalculator;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingOperationConfiguration;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualUpfrontDiscountCandidateFinderTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 2, 10);
    private static final Instant OLD = Instant.parse("2027-01-05T10:00:00Z");
    private static final Instant RECENT = Instant.parse("2027-01-20T10:00:00Z");
    private static final LongUnaryOperator NO_PLACES_USED = id -> 0L;

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());

    private static DiscountDraftBuilder annual() {
        return DiscountDraftBuilder.annualUpfront().availableFrom(LocalDate.of(2027, 1, 1));
    }

    private static DiscountDefinition definition(long id, DiscountDraftBuilder draft, Instant createdAt) {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(draft.build(), 99L, createdAt);
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }

    private static List<NonNegativeMoney> tenLinesOf(String amount) {
        return IntStream.range(0, 10).mapToObj(index -> NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS)).toList();
    }

    private Map<Integer, InvoiceDiscountCandidate> winnersOf(List<DiscountDefinition> definitions, LongUnaryOperator placesUsed) {
        return calculator.selectBestDiscountPerInvoiceLine(InvoiceType.ANNUAL_UPFRONT,
                AnnualUpfrontDiscountCandidateFinder.candidatesOf(definitions, 7L, Currency.ARS, TODAY, tenLinesOf("80000.00"), placesUsed));
    }

    @Test
    @Tag("D-295")
    void aPercentagePromotionSavesTheSameAmountOnEveryLine() {
        Map<Integer, InvoiceDiscountCandidate> winners = winnersOf(List.of(definition(1, annual().withPercent("10"), OLD)), NO_PLACES_USED);

        assertThat(winners).hasSize(10);
        assertThat(winners.values()).allSatisfy(winner -> assertThat(winner.discountAmount().value().amount()).isEqualByComparingTo("8000.00"));
    }

    @Test
    @Tag("D-295")
    void theLargestSavingInPesosWinsBetweenAPercentageAndAFixedAmount() {
        DiscountDefinition tenPercent = definition(1, annual().withPercent("10"), OLD);
        DiscountDefinition fixed = definition(2, annual().withFixedAmount("70000.00", Currency.ARS), RECENT);

        Map<Integer, InvoiceDiscountCandidate> winners = winnersOf(List.of(tenPercent, fixed), NO_PLACES_USED);

        assertThat(winners.values()).allSatisfy(winner -> assertThat(winner.sourceId()).isEqualTo(2L));
        assertThat(winnersOf(List.of(fixed, tenPercent), NO_PLACES_USED).values()).allSatisfy(winner -> assertThat(winner.sourceId()).isEqualTo(2L));
    }

    @Test
    @Tag("D-295")
    void aTieOfSavingGoesToThePriorityAndThenToTheOneGrantedFirst() {
        DiscountDefinition lowerPriorityNumber = definition(5, annual().withPercent("10").withPriority(1), RECENT);
        DiscountDefinition higherPriorityNumber = definition(3, annual().withPercent("10").withPriority(2), OLD);
        DiscountDefinition grantedFirst = definition(9, annual().withPercent("10").withPriority(1), OLD);

        assertThat(winnersOf(List.of(higherPriorityNumber, lowerPriorityNumber), NO_PLACES_USED).get(1).sourceId()).isEqualTo(5L);
        assertThat(winnersOf(List.of(lowerPriorityNumber, grantedFirst), NO_PLACES_USED).get(1).sourceId()).isEqualTo(9L);
    }

    @Test
    @Tag("D-295")
    void aPromotionOutsideItsValidityIsNotACandidateAndTheNextOneIsUsed() {
        DiscountDefinition expired = definition(1, annual().withPercent("50").availableUntil(LocalDate.of(2027, 2, 9)), OLD);
        DiscountDefinition upcoming = definition(2, annual().withPercent("40").availableFrom(LocalDate.of(2027, 2, 11)), OLD);
        DiscountDefinition valid = definition(3, annual().withPercent("5"), OLD);

        Map<Integer, InvoiceDiscountCandidate> winners = winnersOf(List.of(expired, upcoming, valid), NO_PLACES_USED);

        assertThat(winners.values()).allSatisfy(winner -> assertThat(winner.sourceId()).isEqualTo(3L));
        assertThat(winnersOf(List.of(expired, upcoming), NO_PLACES_USED)).isEmpty();
    }

    @Test
    @Tag("D-240")
    void aPromotionWithItsQuotaFullIsNotACandidateAndTheNextOneIsUsed() {
        DiscountDefinition limited = definition(1, annual().withPercent("50").withQuota(3), OLD);
        DiscountDefinition unlimited = definition(2, annual().withPercent("5"), OLD);

        assertThat(winnersOf(List.of(limited, unlimited), id -> id == 1L ? 2L : 0L).get(1).sourceId()).isEqualTo(1L);
        assertThat(winnersOf(List.of(limited, unlimited), id -> id == 1L ? 3L : 0L).get(1).sourceId()).isEqualTo(2L);
    }

    @Test
    @Tag("D-295")
    void aPromotionScopedToAnotherProgramOrCurrencyIsNotACandidate() {
        DiscountDefinition otherProgram = definition(1, annual().withPercent("50").forProgram(8L), OLD);
        DiscountDefinition sameProgram = definition(2, annual().withPercent("20").forProgram(7L), OLD);
        DiscountDefinition dollars = definition(3, annual().withFixedAmount("60000.00", Currency.USD), OLD);

        Map<Integer, InvoiceDiscountCandidate> winners = winnersOf(List.of(otherProgram, dollars, sameProgram), NO_PLACES_USED);

        assertThat(winners.values()).allSatisfy(winner -> assertThat(winner.sourceId()).isEqualTo(2L));
    }

    @Test
    @Tag("D-099")
    void aFixedAmountNeverExceedsTheLinePrice() {
        DiscountDefinition fixed = definition(1, annual().withFixedAmount("900000.00", Currency.ARS), OLD);

        assertThat(winnersOf(List.of(fixed), NO_PLACES_USED).values()).allSatisfy(winner -> assertThat(winner.discountAmount().value().amount()).isEqualByComparingTo("80000.00"));
    }
}
