package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountQuotaCostEstimate;
import com.red_escuela.backend.billing.discount.domain.DiscountQuotaCostStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-240")
@Tag("D-326")
class DiscountQuotaCostEstimatorTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);
    private static final NonNegativeMoney PRICE = NonNegativeMoney.of(new BigDecimal("100000.00"), Currency.ARS);

    private final PriceReader priceReader = mock(PriceReader.class);
    private final DiscountQuotaCostEstimator estimator = new DiscountQuotaCostEstimator(priceReader);

    private static DiscountDefinition definitionOf(DiscountDefinitionDraft draft) {
        return DiscountDefinition.publishDiscountDefinition(draft, 1L, Instant.parse("2031-03-10T10:00:00Z"));
    }

    @Test
    void aDefinitionWithQuotaButWithoutAnOfferingScopeIsNotApplicableAndNeverAsksForAPrice() {
        DiscountDefinition definition = definitionOf(DiscountDraftBuilder.cohortPromotion().withoutScope().withQuota(5).build());

        DiscountQuotaCostEstimate estimate = estimator.estimateQuotaCostOn(definition, TODAY);

        assertThat(estimate.status()).isEqualTo(DiscountQuotaCostStatus.NOT_APPLICABLE);
        assertThat(estimate.cost()).isEmpty();
        verifyNoInteractions(priceReader);
    }

    @Test
    void aDefinitionOnAnInvoiceTypeWithoutAFeeIsNotApplicableAndNeverAsksForAPrice() {
        DiscountDefinition definition = definitionOf(DiscountDraftBuilder.cohortPromotion().forInvoiceType(InvoiceType.ANNUAL_UPFRONT).withoutInstallments().withQuota(5).build());

        assertThat(estimator.estimateQuotaCostOn(definition, TODAY).status()).isEqualTo(DiscountQuotaCostStatus.NOT_APPLICABLE);
        verifyNoInteractions(priceReader);
    }

    @Test
    void aDefinitionWithoutQuotaHasNoQuotaEvenWithoutAScope() {
        DiscountDefinition definition = definitionOf(DiscountDraftBuilder.annualUpfront().build());

        assertThat(estimator.estimateQuotaCostOn(definition, TODAY).status()).isEqualTo(DiscountQuotaCostStatus.NO_QUOTA);
        verifyNoInteractions(priceReader);
    }

    @Test
    void aScopedDefinitionAsksForTheFeePriceOfItsOwnOfferingOnTheGivenDay() {
        DiscountDefinition definition = definitionOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).withQuota(2).build());
        PriceVersionResolution resolution = new PriceVersionResolution(9L, PRICE, NonNegativeMoney.of(BigDecimal.ZERO, Currency.ARS), PRICE);
        when(priceReader.findPriceVersionEffectiveOn(any(), any(), any())).thenReturn(Optional.of(resolution));

        DiscountQuotaCostEstimate estimate = estimator.estimateQuotaCostOn(definition, TODAY);

        verify(priceReader).findPriceVersionEffectiveOn(OfferingReference.ofProgram(4L), FeeType.MONTHLY_INSTALLMENT, TODAY);
        assertThat(estimate.status()).isEqualTo(DiscountQuotaCostStatus.ESTIMATED);
        assertThat(estimate.cost().orElseThrow().value().amount()).isEqualByComparingTo("60000.00");
    }

    @Test
    void aScopedDefinitionWithoutAPriceInForceReportsNoEffectivePrice() {
        DiscountDefinition definition = definitionOf(DiscountDraftBuilder.cohortPromotion().forCourse(7L).withQuota(2).build());
        when(priceReader.findPriceVersionEffectiveOn(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(estimator.estimateQuotaCostOn(definition, TODAY).status()).isEqualTo(DiscountQuotaCostStatus.NO_EFFECTIVE_PRICE);
    }
}
