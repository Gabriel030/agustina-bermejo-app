package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-532")
@Tag("D-035")
@Tag("D-017")
class PriceLectiveYearVersionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate JANUARY = LocalDate.of(2027, 1, 1);
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Long DIRECTOR = 9L;

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static long amountOn(Price price, LocalDate date) {
        return price.findVersionEffectiveOn(date).orElseThrow().getBaseAmount().value().amount().longValue();
    }

    private static Price priceWithCurrentVersion() {
        Price price = Price.forProgram(1L, FeeType.ENROLLMENT_FEE, Currency.ARS, "Matrícula");
        price.publishPriceVersion(ars(100), null, TODAY, "v1", DIRECTOR, true, TODAY, NOW);
        return price;
    }

    private static PriceVersion publishTagged(Price price, long amount, LocalDate from, int year) {
        return price.publishPriceVersion(ars(amount), null, from, "año " + year, DIRECTOR, false, TODAY, NOW, year);
    }

    @Test
    void aTaggedVersionPublishedInAdvanceStaysScheduledAndDoesNotChangeTheRegularPrice() {
        Price price = priceWithCurrentVersion();
        PriceVersion regular = price.getVersions().get(0);

        PriceVersion tagged = publishTagged(price, 150, JANUARY, 2027);

        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(tagged.getAppliesToLectiveYear()).isEqualTo(2027);
        assertThat(regular.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(amountOn(price, TODAY)).isEqualTo(100);
        assertThat(amountOn(price, JANUARY.minusDays(1))).isEqualTo(100);
    }

    @Test
    void aRegularVersionPublishedLaterDoesNotSupersedeTheTaggedOne() {
        Price price = priceWithCurrentVersion();
        PriceVersion tagged = publishTagged(price, 150, JANUARY, 2027);

        Instant later = NOW.plusSeconds(60);
        price.makeRoomForVersionStartingOn(TODAY, TODAY, DIRECTOR, later);
        price.publishPriceVersion(ars(120), null, TODAY, "ajuste", DIRECTOR, false, TODAY, later);

        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(amountOn(price, TODAY)).isEqualTo(120);
        assertThat(price.findNextScheduledVersionAfter(TODAY)).contains(tagged);
    }

    @Test
    void theTaggedVersionTakesOverOnItsStartDateByTheNormalActivation() {
        Price price = priceWithCurrentVersion();
        PriceVersion regular = price.getVersions().get(0);
        PriceVersion tagged = publishTagged(price, 150, JANUARY, 2027);

        price.activateDueVersions(JANUARY.minusDays(1), NOW);
        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);

        price.activateDueVersions(JANUARY, NOW);

        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(regular.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(amountOn(price, JANUARY)).isEqualTo(150);
    }

    @Test
    void whatWasResolvedBeforeTheTaggedVersionKeepsItsVersionAndAmount() {
        Price price = priceWithCurrentVersion();
        PriceVersion resolvedBefore = price.findVersionEffectiveOn(TODAY).orElseThrow();

        publishTagged(price, 150, JANUARY, 2027);

        assertThat(price.findVersionEffectiveOn(TODAY).orElseThrow()).isSameAs(resolvedBefore);
        assertThat(resolvedBefore.getBaseAmount()).isEqualTo(ars(100));
    }

    @Test
    void aTaggedVersionCannotStartTodayOrBefore() {
        Price price = priceWithCurrentVersion();

        assertThatThrownBy(() -> publishTagged(price, 150, TODAY, 2027)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.proposePriceVersion(ars(150), null, TODAY, "x", 5L, 77L, false, TODAY, NOW, 2027)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishingAnotherVersionForTheSameYearCancelsTheScheduledOneLeavingASingleLiveOne() {
        Price price = priceWithCurrentVersion();
        PriceVersion first = publishTagged(price, 150, JANUARY, 2027);

        PriceVersion second = publishTagged(price, 170, LocalDate.of(2026, 12, 1), 2027);

        assertThat(first.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(price.getVersions().stream().filter(v -> Integer.valueOf(2027).equals(v.getAppliesToLectiveYear()))
                .filter(v -> v.getStatus() == PriceVersionStatus.SCHEDULED)).hasSize(1);
        assertThat(amountOn(price, TODAY)).isEqualTo(100);
    }

    @Test
    void aVersionForAnotherYearIsNotReplaced() {
        Price price = priceWithCurrentVersion();
        PriceVersion year2027 = publishTagged(price, 150, JANUARY, 2027);

        publishTagged(price, 300, LocalDate.of(2028, 1, 1), 2028);

        assertThat(year2027.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
    }

    @Test
    void aSameDateTaggedAndRegularVersionDoNotCancelEachOther() {
        Price price = priceWithCurrentVersion();
        PriceVersion tagged = publishTagged(price, 150, JANUARY, 2027);
        price.makeRoomForVersionStartingOn(JANUARY, TODAY, DIRECTOR, NOW);
        PriceVersion regular = price.publishPriceVersion(ars(130), null, JANUARY, "regular", DIRECTOR, false, TODAY, NOW);

        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(regular.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);

        PriceVersion taggedAgain = publishTagged(price, 160, JANUARY, 2027);
        assertThat(regular.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(taggedAgain.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(tagged.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
    }

    @Test
    void approvingATaggedProposalReplacesTheScheduledVersionOfTheSameYear() {
        Price price = priceWithCurrentVersion();
        PriceVersion scheduled = publishTagged(price, 150, JANUARY, 2027);
        PriceVersion proposal = price.proposePriceVersion(ars(180), null, LocalDate.of(2026, 12, 1), "ajuste", 5L, 77L, false, TODAY, NOW, 2027);

        price.approveProposedVersion(proposal, DIRECTOR, null, TODAY, NOW);

        assertThat(scheduled.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(proposal.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(amountOn(price, TODAY)).isEqualTo(100);
    }
}
