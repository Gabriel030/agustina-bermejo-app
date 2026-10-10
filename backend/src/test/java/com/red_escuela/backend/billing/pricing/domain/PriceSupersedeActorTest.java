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

@Tag("D-214")
class PriceSupersedeActorTest {
    private static final LocalDate DAY = LocalDate.of(2027, 3, 1);
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static PriceVersionStatusChange supersession(PriceVersion version) {
        return version.getStatusChanges().stream().filter(change -> change.getToStatus() == PriceVersionStatus.SUPERSEDED).findFirst().orElseThrow();
    }

    @Test
    void thePersonWhoPublishesTheNewVersionIsTheActorOfTheSupersessionOfTheOldOne() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        PriceVersion old = price.publishPriceVersion(ars(100000), null, DAY, "v1", 9L, true, DAY, NOW);

        price.publishPriceVersion(ars(120000), null, DAY, "v2", 7L, false, DAY, NOW.plusSeconds(5));

        assertThat(supersession(old).getActorPersonId()).isEqualTo(7L);
        assertThat(supersession(old).getActorProcess()).isNull();
    }

    @Test
    void thePersonWhoApprovesTheProposalIsTheActorOfTheSupersessionOfTheOldVersion() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        PriceVersion old = price.publishPriceVersion(ars(100000), null, DAY, "v1", 9L, true, DAY, NOW);
        PriceVersion proposal = price.proposePriceVersion(ars(120000), null, DAY, "v2", 5L, 33L, false, DAY, NOW.plusSeconds(1));

        price.approveProposedVersion(proposal, 8L, "ok", DAY, NOW.plusSeconds(5));

        assertThat(supersession(old).getActorPersonId()).isEqualTo(8L);
        assertThat(supersession(old).getActorProcess()).isNull();
    }

    @Test
    void theActivationOfAScheduledVersionSupersedesTheOldOneAsTheActivationProcess() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        PriceVersion old = price.publishPriceVersion(ars(100000), null, DAY, "v1", 9L, true, DAY, NOW);
        PriceVersion scheduled = price.publishPriceVersion(ars(120000), null, DAY.plusDays(30), "v2", 9L, false, DAY, NOW.plusSeconds(1));

        price.activateScheduledVersion(scheduled, NOW.plusSeconds(10));

        assertThat(supersession(old).getActorProcess()).isEqualTo("PriceVersionActivation");
        assertThat(supersession(old).getActorPersonId()).isNull();
    }
}
