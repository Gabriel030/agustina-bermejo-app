package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-214")
@Tag("D-035")
@Tag("D-240")
class PriceEffectiveVersionResolutionTest {
    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static Price priceWithV1AndScheduledV2() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        price.publishPriceVersion(ars(100000), null, LocalDate.of(2027, 3, 1), "v1", 9L, true, LocalDate.of(2027, 3, 1), NOW);
        price.publishPriceVersion(ars(120000), null, LocalDate.of(2027, 9, 1), "v2", 9L, false, LocalDate.of(2027, 3, 1), NOW.plusSeconds(1));
        return price;
    }

    @Test
    void theVersionWithTheLatestStartNotAfterTheDateRulesEvenWhileStillScheduled() {
        Price price = priceWithV1AndScheduledV2();

        assertThat(price.findVersionEffectiveOn(LocalDate.of(2027, 8, 31)).orElseThrow().finalAmount()).isEqualTo(ars(100000));
        PriceVersion onTheStartDay = price.findVersionEffectiveOn(LocalDate.of(2027, 9, 1)).orElseThrow();
        assertThat(onTheStartDay.finalAmount()).isEqualTo(ars(120000));
        assertThat(onTheStartDay.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(price.findVersionEffectiveOn(LocalDate.of(2027, 2, 28))).isEmpty();
    }

    @Test
    void theStartDayIsTheBuenosAiresDayEvenWhenTheJvmRunsInUtc() {
        Price price = priceWithV1AndScheduledV2();
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
            ZonedDateTime buenosAiresHalfPastMidnight = ZonedDateTime.of(2027, 9, 1, 0, 30, 0, 0, BUENOS_AIRES);
            LocalDate buenosAiresDay = buenosAiresHalfPastMidnight.toLocalDate();
            LocalDate jvmDefaultDayAtThatInstant = buenosAiresHalfPastMidnight.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            assertThat(buenosAiresDay).isEqualTo(LocalDate.of(2027, 9, 1));
            assertThat(price.findVersionEffectiveOn(buenosAiresDay.minusDays(1)).orElseThrow().finalAmount()).isEqualTo(ars(100000));
            assertThat(price.findVersionEffectiveOn(buenosAiresDay).orElseThrow().finalAmount()).isEqualTo(ars(120000));
            assertThat(jvmDefaultDayAtThatInstant).isEqualTo(LocalDate.of(2027, 9, 1));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void twoVersionsOfTheSameDayResolveToTheNewestDecision() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        LocalDate day = LocalDate.of(2027, 3, 1);
        price.publishPriceVersion(ars(100000), null, day, "first", 9L, true, day, NOW);
        PriceVersion second = price.publishPriceVersion(ars(110000), null, day, "second", 9L, false, day, NOW.plusSeconds(5));

        assertThat(price.findVersionEffectiveOn(day).orElseThrow()).isSameAs(second);
        assertThat(price.getVersions().get(0).getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(price.findVersionEffectiveOn(day.plusDays(40)).orElseThrow()).isSameAs(second);
    }

    @ParameterizedTest
    @EnumSource(value = PriceVersionStatus.class, names = {"CANCELLED", "REJECTED", "WITHDRAWN", "EXPIRED", "PROPOSED"})
    void versionsThatNeverRoseAreIgnoredAndThePreviousApprovedOneRules(PriceVersionStatus ignoredStatus) {
        LocalDate today = LocalDate.of(2027, 3, 1);
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        price.publishPriceVersion(ars(100000), null, today, "v1", 9L, true, today, NOW);
        PriceVersion proposal = price.proposePriceVersion(ars(150000), null, today.plusDays(10), "raise", 3L, 77L, false, today, NOW.plusSeconds(1));
        switch (ignoredStatus) {
            case CANCELLED -> {
                price.approveProposedVersion(proposal, 9L, "ok", today, NOW.plusSeconds(2));
                price.cancelScheduledVersion(proposal, 9L, "no longer", NOW.plusSeconds(3));
            }
            case REJECTED -> price.rejectProposedVersion(proposal, 9L, "no", NOW.plusSeconds(2));
            case WITHDRAWN -> price.withdrawProposedVersion(proposal, 3L, NOW.plusSeconds(2));
            case EXPIRED -> price.expireProposedVersion(proposal, NOW.plusSeconds(2));
            default -> {
            }
        }

        assertThat(proposal.getStatus()).isEqualTo(ignoredStatus);
        assertThat(price.findVersionEffectiveOn(today.plusDays(30)).orElseThrow().finalAmount()).isEqualTo(ars(100000));
    }

    @Test
    void thePublishedFinalAmountIsTheBaseMinusTheMarketingDiscount() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        LocalDate day = LocalDate.of(2027, 3, 1);
        PriceVersion version = price.publishPriceVersion(ars(100000), MarketingDiscount.ofPercentage(Percentage.ofPoints(BigDecimal.valueOf(50))), day, "ad", 9L, false, day, NOW);

        assertThat(version.getBaseAmount()).isEqualTo(ars(100000));
        assertThat(version.marketingDiscountAmount()).isEqualTo(ars(50000));
        assertThat(version.finalAmount()).isEqualTo(ars(50000));
        assertThat(version.resolution().listAmount()).isEqualTo(ars(100000));
    }

    @Test
    void aVersionCannotStartInThePastAndMustUseTheCurrencyOfThePrice() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        LocalDate today = LocalDate.of(2027, 3, 1);

        assertThatThrownBy(() -> price.publishPriceVersion(ars(1), null, today.minusDays(1), "late", 9L, false, today, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.publishPriceVersion(NonNegativeMoney.of(BigDecimal.ONE, Currency.USD), null, today, "usd", 9L, false, today, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
