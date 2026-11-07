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

@Tag("D-532")
@Tag("D-035")
class PriceLectiveYearVersionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate JANUARY = LocalDate.of(2027, 1, 1);
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Long DIRECTOR = 9L;

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static Price priceWithCurrentVersion() {
        Price price = Price.forProgram(1L, FeeType.ENROLLMENT_FEE, Currency.ARS, "Matrícula");
        price.publishPriceVersion(ars(100), null, TODAY, "v1", DIRECTOR, true, TODAY, NOW);
        return price;
    }

    @Test
    void aVersionPublishedForALectiveYearKeepsItsTagAndStaysScheduled() {
        Price price = priceWithCurrentVersion();

        PriceVersion version = price.publishPriceVersion(ars(150), null, JANUARY, "2027", DIRECTOR, false, TODAY, NOW, 2027);

        assertThat(version.getAppliesToLectiveYear()).isEqualTo(2027);
        assertThat(version.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
    }

    @Test
    void anUntaggedVersionHasNoLectiveYear() {
        Price price = priceWithCurrentVersion();

        assertThat(price.getVersions().get(0).getAppliesToLectiveYear()).isNull();
    }

    @Test
    void publishingAnotherVersionForTheSameYearCancelsTheScheduledOneAndLeavesASingleLiveVersion() {
        Price price = priceWithCurrentVersion();
        PriceVersion first = price.publishPriceVersion(ars(150), null, JANUARY, "2027", DIRECTOR, false, TODAY, NOW, 2027);

        PriceVersion second = price.publishPriceVersion(ars(170), null, LocalDate.of(2026, 12, 1), "2027 corregido", DIRECTOR, false, TODAY, NOW, 2027);

        assertThat(first.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(price.getVersions().stream().filter(v -> Integer.valueOf(2027).equals(v.getAppliesToLectiveYear()))
                .filter(v -> v.getStatus() == PriceVersionStatus.SCHEDULED || v.getStatus() == PriceVersionStatus.EFFECTIVE)).hasSize(1);
    }

    @Test
    void aVersionForAnotherYearIsNotReplaced() {
        Price price = priceWithCurrentVersion();
        PriceVersion year2027 = price.publishPriceVersion(ars(150), null, JANUARY, "2027", DIRECTOR, false, TODAY, NOW, 2027);

        price.publishPriceVersion(ars(300), null, LocalDate.of(2028, 1, 1), "2028", DIRECTOR, false, TODAY, NOW, 2028);

        assertThat(year2027.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
    }

    @Test
    void anAlreadyEmittedPriceKeepsBeingResolvableAfterTheYearVersionIsReplaced() {
        Price price = priceWithCurrentVersion();
        PriceVersion first = price.publishPriceVersion(ars(150), null, TODAY, "2027 hoy", DIRECTOR, false, TODAY, NOW, 2027);

        price.publishPriceVersion(ars(170), null, JANUARY, "2027 nuevo", DIRECTOR, false, TODAY, NOW, 2027);

        assertThat(first.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
        assertThat(first.getBaseAmount()).isEqualTo(ars(150));
    }

    @Test
    void approvingATaggedProposalReplacesTheLiveVersionOfTheSameYear() {
        Price price = priceWithCurrentVersion();
        PriceVersion live = price.publishPriceVersion(ars(150), null, JANUARY, "2027", DIRECTOR, false, TODAY, NOW, 2027);
        PriceVersion proposal = price.proposePriceVersion(ars(180), null, LocalDate.of(2026, 12, 1), "ajuste", 5L, 77L, false, TODAY, NOW, 2027);

        price.approveProposedVersion(proposal, DIRECTOR, null, TODAY, NOW);

        assertThat(live.getStatus()).isEqualTo(PriceVersionStatus.CANCELLED);
        assertThat(proposal.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(proposal.getAppliesToLectiveYear()).isEqualTo(2027);
    }
}
