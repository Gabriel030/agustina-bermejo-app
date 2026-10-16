package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.enrollment.api.ActiveEnrollmentCounter;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceChangeImpactCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);

    private final PriceRepository priceRepository = mock(PriceRepository.class);
    private final ActiveEnrollmentCounter counter = mock(ActiveEnrollmentCounter.class);
    private final PriceVersionPolicyEvaluator policyEvaluator = mock(PriceVersionPolicyEvaluator.class);
    private PriceChangeImpactCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PriceChangeImpactCalculator(priceRepository, counter, policyEvaluator, Clock.fixed(NOW, ZoneOffset.UTC));
        doNothing().when(policyEvaluator).requireValidPriceVersion(any());
    }

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private Price priceWithCurrent(Price price, long amount) {
        price.publishPriceVersion(ars(amount), null, TODAY, "inicial", 1L, true, TODAY, NOW);
        when(priceRepository.findById(7L)).thenReturn(Optional.of(price));
        return price;
    }

    @Test
    @Tag("D-274")
    void aCourseInstallmentCountsTheActiveEnrollmentsOfTheCourseAndNotOfAnyProgram() {
        priceWithCurrent(Price.forCourse(9L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual"), 100000);
        when(counter.countActiveCourseEnrollments(9L)).thenReturn(40L);

        PriceChangeImpact impact = calculator.calculatePriceChangeImpact(7L, ars(120000), null, TODAY.plusDays(10));

        assertThat(impact.affectedStudents()).isEqualTo(40);
        assertThat(impact.monthlyIncomeDifference()).isEqualTo(Money.of(BigDecimal.valueOf(800000), Currency.ARS));
        verify(counter, never()).countActiveProgramEnrollments(any());
    }

    @Test
    @Tag("D-274")
    void aPriceWithoutAnyEffectiveVersionHasNoCurrentAmountAndItsWholeProposedIncomeIsTheDifference() {
        Price price = Price.forProgram(3L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        when(priceRepository.findById(7L)).thenReturn(Optional.of(price));
        when(counter.countActiveProgramEnrollments(3L)).thenReturn(2L);

        PriceChangeImpact impact = calculator.calculatePriceChangeImpact(7L, ars(50000), null, TODAY);

        assertThat(impact.currentFinalAmount()).isNull();
        assertThat(impact.currentMonthlyIncome()).isEqualTo(Money.zero(Currency.ARS));
        assertThat(impact.monthlyIncomeDifference()).isEqualTo(Money.of(BigDecimal.valueOf(100000), Currency.ARS));
    }

    @Test
    @Tag("D-240")
    void theFixedMarketingDiscountIsGivenUpOncePerAffectedStudent() {
        priceWithCurrent(Price.forProgram(3L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual"), 100000);
        when(counter.countActiveProgramEnrollments(3L)).thenReturn(4L);
        MarketingDiscount discount = MarketingDiscount.ofFixedAmount(ars(30000));

        PriceChangeImpact impact = calculator.calculatePriceChangeImpact(7L, ars(100000), discount, TODAY);

        assertThat(impact.proposedFinalAmount()).isEqualTo(ars(70000));
        assertThat(impact.monthlyMarketingDiscountCost()).isEqualTo(Money.of(BigDecimal.valueOf(120000), Currency.ARS));
        assertThat(impact.monthlyIncomeDifference()).isEqualTo(Money.of(BigDecimal.valueOf(-120000), Currency.ARS));
    }

    @Test
    @Tag("D-240")
    void aPercentageDiscountIsAppliedOverTheBaseOfTheProposedVersion() {
        priceWithCurrent(Price.forProgram(3L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual"), 100000);
        when(counter.countActiveProgramEnrollments(3L)).thenReturn(1L);

        PriceChangeImpact impact = calculator.calculatePriceChangeImpact(7L, ars(200000), MarketingDiscount.ofPercentage(Percentage.ofPoints(BigDecimal.valueOf(25))), TODAY);

        assertThat(impact.proposedFinalAmount()).isEqualTo(ars(150000));
        assertThat(impact.monthlyMarketingDiscountCost()).isEqualTo(Money.of(BigDecimal.valueOf(50000), Currency.ARS));
    }

    @Test
    @Tag("D-274")
    void aNonRecurringFeeNeverQueriesTheEnrollments() {
        priceWithCurrent(Price.forProgram(3L, FeeType.ENROLLMENT_FEE, Currency.ARS, "Matrícula"), 100000);

        PriceChangeImpact impact = calculator.calculatePriceChangeImpact(7L, ars(150000), null, TODAY);

        assertThat(impact.reachesActiveEnrollments()).isFalse();
        assertThat(impact.affectedStudents()).isZero();
        verify(counter, never()).countActiveProgramEnrollments(any());
        verify(counter, never()).countActiveCourseEnrollments(any());
    }
}
