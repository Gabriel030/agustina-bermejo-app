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

@Tag("D-114")
@Tag("D-116")
@Tag("D-225")
@Tag("D-180")
class PriceProposalLifecycleTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2027, 9, 1);
    private static final Instant NOW = Instant.parse("2027-08-20T12:00:00Z");
    private static final Long ADMINISTRATIVE = 7L;
    private static final Long DIRECTOR = 9L;

    private static NonNegativeMoney ars(long amount) {
        return NonNegativeMoney.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    private static Price priceWithEffectiveVersion() {
        Price price = Price.forProgram(1L, FeeType.MONTHLY_INSTALLMENT, Currency.ARS, "Cuota mensual");
        price.publishPriceVersion(ars(100000), null, TODAY, "v1", DIRECTOR, true, TODAY, NOW);
        return price;
    }

    private static PriceVersion proposal(Price price, long requestId, LocalDate start) {
        return price.proposePriceVersion(ars(120000), null, start, "aumento", ADMINISTRATIVE, requestId, false, TODAY, NOW);
    }

    @Test
    void aProposalIsOpenAndFoundOnlyByItsOwnAuthorizationRequestWithoutChangingTheEffectivePrice() {
        Price price = priceWithEffectiveVersion();

        PriceVersion proposal = proposal(price, 31L, SEPTEMBER_FIRST);

        assertThat(proposal.getStatus()).isEqualTo(PriceVersionStatus.PROPOSED);
        assertThat(proposal.isDirectPublication()).isFalse();
        assertThat(price.findOpenProposalOfRequest(31L)).containsSame(proposal);
        assertThat(price.findOpenProposalOfRequest(32L)).isEmpty();
        assertThat(price.findOpenProposal()).containsSame(proposal);
        assertThat(price.findVersionEffectiveOn(SEPTEMBER_FIRST).orElseThrow().finalAmount()).isEqualTo(ars(100000));
    }

    @Test
    void approvingAProposalWithAFutureStartSchedulesItAndKeepsTheEffectiveVersion() {
        Price price = priceWithEffectiveVersion();
        PriceVersion proposal = proposal(price, 31L, SEPTEMBER_FIRST);

        price.approveProposedVersion(proposal, DIRECTOR, null, TODAY, NOW.plusSeconds(60));

        assertThat(proposal.getStatus()).isEqualTo(PriceVersionStatus.SCHEDULED);
        assertThat(proposal.getDecidedById()).isEqualTo(DIRECTOR);
        assertThat(price.findOpenProposalOfRequest(31L)).isEmpty();
        assertThat(price.getVersions()).filteredOn(version -> version.getStatus() == PriceVersionStatus.EFFECTIVE).hasSize(1);
        assertThat(price.findVersionEffectiveOn(SEPTEMBER_FIRST.minusDays(1)).orElseThrow().getBaseAmount()).isEqualTo(ars(100000));
        assertThat(price.findVersionEffectiveOn(SEPTEMBER_FIRST).orElseThrow()).isSameAs(proposal);
    }

    @Test
    void approvingAProposalThatStartsTodayMakesItEffectiveAndSupersedesThePreviousOne() {
        Price price = priceWithEffectiveVersion();
        PriceVersion previous = price.findVersionEffectiveOn(TODAY).orElseThrow();
        PriceVersion proposal = proposal(price, 31L, TODAY);

        price.approveProposedVersion(proposal, DIRECTOR, null, TODAY, NOW.plusSeconds(60));

        assertThat(proposal.getStatus()).isEqualTo(PriceVersionStatus.EFFECTIVE);
        assertThat(previous.getStatus()).isEqualTo(PriceVersionStatus.SUPERSEDED);
    }

    @Test
    void rejectingWithdrawingAndExpiringCloseTheProposalWithoutTouchingTheEffectivePrice() {
        Price price = priceWithEffectiveVersion();
        PriceVersion rejected = proposal(price, 31L, SEPTEMBER_FIRST);
        price.rejectProposedVersion(rejected, DIRECTOR, "caro", NOW);
        PriceVersion withdrawn = proposal(price, 32L, SEPTEMBER_FIRST);
        price.withdrawProposedVersion(withdrawn, ADMINISTRATIVE, NOW);
        PriceVersion expired = proposal(price, 33L, SEPTEMBER_FIRST);
        price.expireProposedVersion(expired, NOW);

        assertThat(rejected.getStatus()).isEqualTo(PriceVersionStatus.REJECTED);
        assertThat(rejected.getDecisionReason()).isEqualTo("caro");
        assertThat(withdrawn.getStatus()).isEqualTo(PriceVersionStatus.WITHDRAWN);
        assertThat(expired.getStatus()).isEqualTo(PriceVersionStatus.EXPIRED);
        assertThat(price.findOpenProposal()).isEmpty();
        assertThat(price.findVersionEffectiveOn(SEPTEMBER_FIRST).orElseThrow().getBaseAmount()).isEqualTo(ars(100000));
    }

    @Test
    void aClosedProposalCannotBeDecidedAgain() {
        Price price = priceWithEffectiveVersion();
        PriceVersion proposal = proposal(price, 31L, SEPTEMBER_FIRST);
        price.withdrawProposedVersion(proposal, ADMINISTRATIVE, NOW);

        assertThatThrownBy(() -> price.approveProposedVersion(proposal, DIRECTOR, null, TODAY, NOW)).isInstanceOf(IllegalPriceVersionStatusTransitionException.class);
        assertThatThrownBy(() -> price.rejectProposedVersion(proposal, DIRECTOR, "x", NOW)).isInstanceOf(IllegalPriceVersionStatusTransitionException.class);
    }

    @Test
    void aProposalStartingYesterdayOrInAnotherCurrencyIsRefused() {
        Price price = priceWithEffectiveVersion();

        assertThatThrownBy(() -> price.proposePriceVersion(ars(120000), null, TODAY.minusDays(1), "x", ADMINISTRATIVE, 31L, false, TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.proposePriceVersion(NonNegativeMoney.of(BigDecimal.ONE, Currency.USD), null, SEPTEMBER_FIRST, "x", ADMINISTRATIVE, 31L, false, TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
