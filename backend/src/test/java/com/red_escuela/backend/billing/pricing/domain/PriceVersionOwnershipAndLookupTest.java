package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceVersionOwnershipAndLookupTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2027, 9, 1);
    private static final LocalDate OCTOBER_FIRST = LocalDate.of(2027, 10, 1);
    private static final Instant NOW = Instant.parse("2027-08-20T12:00:00Z");

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static Price priceWithEffectiveVersion() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        price.publishPriceVersion(ars(100000), null, TODAY, "v1", 9L, true, TODAY, NOW);
        return price;
    }

    private static PriceVersion foreignProposal() {
        Price other = Price.forProgram(2L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Otra");
        return other.proposePriceVersion(ars(1), null, SEPTEMBER_FIRST, "x", 7L, 99L, true, TODAY, NOW);
    }

    @Test
    void aCoursePriceKeepsTheCourseAndNoProgram() {
        Price price = Price.forCourse(5L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Curso");

        assertThat(price.getCourseId()).isEqualTo(5L);
        assertThat(price.getProgramId()).isNull();
    }

    @Test
    void everyVersionTransitionRejectsAVersionOfAnotherPrice() {
        Price price = priceWithEffectiveVersion();
        PriceVersion foreign = foreignProposal();

        assertThatThrownBy(() -> price.approveProposedVersion(foreign, 9L, "ok", TODAY, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.rejectProposedVersion(foreign, 9L, "no", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.withdrawProposedVersion(foreign, 7L, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.expireProposedVersion(foreign, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.cancelScheduledVersion(foreign, 9L, "no", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.activateScheduledVersion(foreign, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(foreign.getStatus()).isEqualTo(PriceVersionStatus.PROPOSED);
    }

    @Test
    void theNextScheduledVersionIsTheEarliestStrictlyAfterTheGivenDate() {
        Price price = priceWithEffectiveVersion();
        PriceVersion september = price.publishPriceVersion(ars(110000), null, SEPTEMBER_FIRST, "v2", 9L, false, TODAY, NOW);
        PriceVersion october = price.publishPriceVersion(ars(120000), null, OCTOBER_FIRST, "v3", 9L, false, TODAY, NOW);

        assertThat(price.findNextScheduledVersionAfter(TODAY)).containsSame(september);
        assertThat(price.findNextScheduledVersionAfter(SEPTEMBER_FIRST)).containsSame(october);
        assertThat(price.findNextScheduledVersionAfter(OCTOBER_FIRST)).isEmpty();
    }

    @Test
    void versionsAreListedNewestFirst() {
        Price price = priceWithEffectiveVersion();
        PriceVersion september = price.publishPriceVersion(ars(110000), null, SEPTEMBER_FIRST, "v2", 9L, false, TODAY, NOW);
        PriceVersion october = price.publishPriceVersion(ars(120000), null, OCTOBER_FIRST, "v3", 9L, false, TODAY, NOW);

        assertThat(price.versionsNewestFirst()).hasSize(3).startsWith(october, september);
    }

    @Test
    void retiringEffectiveVersionsSupersedesThem() {
        Price price = priceWithEffectiveVersion();

        price.retireEffectiveVersions(9L, NOW);

        assertThat(price.getVersions()).allSatisfy(version -> assertThat(version.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED));
    }

    @Test
    void activatingDueVersionsReturnsOnlyTheDueOnesAndSupersedesTheOlderEffective() {
        Price price = priceWithEffectiveVersion();
        PriceVersion september = price.publishPriceVersion(ars(110000), null, SEPTEMBER_FIRST, "v2", 9L, false, TODAY, NOW);
        PriceVersion october = price.publishPriceVersion(ars(120000), null, OCTOBER_FIRST, "v3", 9L, false, TODAY, NOW);

        assertThat(price.activateDueVersions(SEPTEMBER_FIRST, NOW)).containsExactly(september);
        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(october.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(price.getVersions().get(0).getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
    }

    @Test
    void activatingAScheduledVersionMakesItEffectiveAndSupersedesTheCurrentOne() {
        Price price = priceWithEffectiveVersion();
        PriceVersion current = price.getVersions().get(0);
        PriceVersion september = price.publishPriceVersion(ars(110000), null, SEPTEMBER_FIRST, "v2", 9L, false, TODAY, NOW);

        price.activateScheduledVersion(september, NOW);

        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(current.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
    }

    @Test
    void makingRoomForAVersionStartingTodaySupersedesTheEffectiveOneAndCancelsAFutureSameDateOne() {
        Price price = priceWithEffectiveVersion();
        PriceVersion current = price.getVersions().get(0);
        PriceVersion september = price.publishPriceVersion(ars(110000), null, SEPTEMBER_FIRST, "v2", 9L, false, TODAY, NOW);

        price.makeRoomForVersionStartingOn(SEPTEMBER_FIRST, TODAY, 9L, NOW);

        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(current.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);

        price.makeRoomForVersionStartingOn(TODAY, TODAY, 9L, NOW);

        assertThat(current.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
    }
}
