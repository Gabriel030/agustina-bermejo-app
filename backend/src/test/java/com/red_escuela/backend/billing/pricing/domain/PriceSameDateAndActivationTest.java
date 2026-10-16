package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-214")
@Tag("D-277")
@Tag("D-280")
@Tag("D-115")
class PriceSameDateAndActivationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2027, 9, 1);
    private static final LocalDate OCTOBER_FIRST = LocalDate.of(2027, 10, 1);
    private static final Instant NOW = Instant.parse("2027-08-20T12:00:00Z");
    private static final Long DIRECTOR = 9L;

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static Price priceWithEffectiveVersion(long amount) {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        price.publishPriceVersion(ars(amount), null, TODAY, "v1", DIRECTOR, true, TODAY, NOW);
        return price;
    }

    private static PriceVersion publish(Price price, long amount, LocalDate start, Instant at) {
        return price.publishPriceVersion(ars(amount), null, start, "cambio", DIRECTOR, false, TODAY, at);
    }

    private static long amountOn(Price price, LocalDate date) {
        return price.findVersionEffectiveOn(date).orElseThrow().getBaseAmount().value().amount().longValue();
    }

    @Test
    void aVersionPublishedForTheSameStartDateReplacesTheScheduledOneWithAnAutomaticReason() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion first = publish(price, 120000, SEPTEMBER_FIRST, NOW.plusSeconds(1));

        PriceVersion second = publish(price, 125000, SEPTEMBER_FIRST, NOW.plusSeconds(2));

        assertThat(first.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(first.getCancellationReason()).isEqualTo("Reemplazada por otra versión con la misma fecha de inicio.");
        assertThat(first.getCancelledById()).isEqualTo(DIRECTOR);
        assertThat(second.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(amountOn(price, SEPTEMBER_FIRST)).isEqualTo(125000);
    }

    @Test
    void aSameDayCorrectionSupersedesTheVersionPublishedEarlierTodayAndTheOldOneKeepsItsStatus() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion morning = publish(price, 110000, TODAY, NOW.plusSeconds(1));

        PriceVersion afternoon = publish(price, 112000, TODAY, NOW.plusSeconds(2));

        assertThat(morning.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(afternoon.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(amountOn(price, TODAY)).isEqualTo(112000);
    }

    @Test
    void aVersionPublishedForADifferentDateDoesNotTouchTheScheduledOne() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion october = publish(price, 120000, OCTOBER_FIRST, NOW.plusSeconds(1));

        PriceVersion september = publish(price, 110000, SEPTEMBER_FIRST, NOW.plusSeconds(2));

        assertThat(october.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(amountOn(price, LocalDate.of(2027, 8, 31))).isEqualTo(100000);
        assertThat(amountOn(price, LocalDate.of(2027, 9, 15))).isEqualTo(110000);
        assertThat(amountOn(price, OCTOBER_FIRST)).isEqualTo(120000);
    }

    @Test
    void activationMovesTheDueVersionToEffectiveAndSupersedesThePreviousOneAsTheActivationProcess() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion old = price.findVersionEffectiveOn(TODAY).orElseThrow();
        PriceVersion scheduled = publish(price, 120000, SEPTEMBER_FIRST, NOW.plusSeconds(1));

        List<PriceVersion> activated = price.activateDueVersions(SEPTEMBER_FIRST, NOW.plusSeconds(10));

        assertThat(activated).containsExactly(scheduled);
        assertThat(scheduled.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(old.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(old.getStatusChanges().get(old.getStatusChanges().size() - 1).getActorProcess()).isEqualTo("PriceVersionActivation");
    }

    @Test
    void activationBeforeTheStartDateChangesNothing() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion scheduled = publish(price, 120000, SEPTEMBER_FIRST, NOW.plusSeconds(1));

        List<PriceVersion> activated = price.activateDueVersions(SEPTEMBER_FIRST.minusDays(1), NOW.plusSeconds(10));

        assertThat(activated).isEmpty();
        assertThat(scheduled.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
    }

    @Test
    void whenSeveralVersionsAreDueOnlyTheNewestStaysEffectiveAndTheOthersPassThroughEffective() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion september = publish(price, 110000, SEPTEMBER_FIRST, NOW.plusSeconds(1));
        PriceVersion october = publish(price, 120000, OCTOBER_FIRST, NOW.plusSeconds(2));

        price.activateDueVersions(OCTOBER_FIRST.plusDays(3), NOW.plusSeconds(10));

        assertThat(october.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(september.getStatusChanges()).extracting(PriceVersionStatusChange::getToStatus)
                .containsExactly(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE, PriceVersionStatus.SUPERSEDED);
        assertThat(price.getVersions().stream().filter(version -> version.getStatus() == PriceVersionStatus.EFFECTIVE)).hasSize(1);
    }

    @Test
    void anEffectiveVersionIsSupersededWhenALaterStartingScheduledOneIsActivated() {
        Price price = priceWithEffectiveVersion(100000);
        PriceVersion september = publish(price, 110000, SEPTEMBER_FIRST, NOW.plusSeconds(1));
        PriceVersion today = publish(price, 130000, TODAY, NOW.plusSeconds(2));

        price.activateDueVersions(SEPTEMBER_FIRST, NOW.plusSeconds(10));

        assertThat(september.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(today.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(amountOn(price, SEPTEMBER_FIRST)).isEqualTo(110000);
    }

    @Test
    void aMissedDueVersionActivatedLateNeverDisplacesAnEffectiveVersionThatStartsAfterIt() {
        Price price = priceWithEffectiveVersion(100000);
        LocalDate missedDay = TODAY.plusDays(1);
        LocalDate laterDay = TODAY.plusDays(5);
        PriceVersion missed = publish(price, 110000, missedDay, NOW.plusSeconds(1));
        PriceVersion published = price.publishPriceVersion(ars(130000), null, laterDay, "cambio", DIRECTOR, false, laterDay, NOW.plusSeconds(2));

        price.activateDueVersions(laterDay, NOW.plusSeconds(10));

        assertThat(published.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(missed.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(missed.getStatusChanges()).extracting(PriceVersionStatusChange::getToStatus)
                .containsExactly(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE, PriceVersionStatus.SUPERSEDED);
    }

    @Test
    void anActivatedVersionIsNotActivatedAgain() {
        Price price = priceWithEffectiveVersion(100000);
        publish(price, 120000, SEPTEMBER_FIRST, NOW.plusSeconds(1));
        price.activateDueVersions(SEPTEMBER_FIRST, NOW.plusSeconds(10));

        List<PriceVersion> second = price.activateDueVersions(SEPTEMBER_FIRST, NOW.plusSeconds(20));

        assertThat(second).isEmpty();
    }
}
