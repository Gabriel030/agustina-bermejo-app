package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.configuration.exception.CancellationReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ChangeReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.RetroactiveEffectiveDateException;
import com.red_escuela.backend.billing.configuration.exception.VersionAlreadyEffectiveException;
import com.red_escuela.backend.billing.pricing.PricingPolicySetsConfiguration;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.billing.pricing.service.PriceVersionPolicyEvaluator;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-095")
@Tag("D-111")
@Tag("D-115")
class PriceChangePoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);
    private static final String MAX_REASON = "m".repeat(PriceChangeReasonPolicy.MAX_REASON_LENGTH);

    private final PricingPolicySetsConfiguration configuration = new PricingPolicySetsConfiguration();
    private final PriceVersionPolicyEvaluator evaluator = new PriceVersionPolicyEvaluator(configuration.priceVersionPolicySet(),
            configuration.priceChangePolicySet(), configuration.priceVersionCancellationPolicySet(), configuration.priceProposalPolicySet(),
            configuration.priceProposalApprovalPolicySet(), configuration.priceProposalRejectionPolicySet());

    @Test
    void aChangeStartingYesterdayIsRejectedAsRetroactiveAndTodayOrTomorrowAreAccepted() {
        assertThatThrownBy(() -> evaluator.requireValidPriceChange(new PriceChangeCheck(TODAY.minusDays(1), TODAY, "motivo")))
                .isInstanceOf(RetroactiveEffectiveDateException.class);

        assertThatCode(() -> evaluator.requireValidPriceChange(new PriceChangeCheck(TODAY, TODAY, "motivo"))).doesNotThrowAnyException();
        assertThatCode(() -> evaluator.requireValidPriceChange(new PriceChangeCheck(TODAY.plusDays(1), TODAY, "motivo"))).doesNotThrowAnyException();
    }

    @Test
    void theChangeReasonIsRequiredAndBoundedByTheColumnLength() {
        for (String invalid : new String[]{null, "", "   ", MAX_REASON + "x"}) {
            assertThatThrownBy(() -> evaluator.requireValidPriceChange(new PriceChangeCheck(TODAY, TODAY, invalid)))
                    .isInstanceOf(ChangeReasonRequiredException.class);
        }

        assertThatCode(() -> evaluator.requireValidPriceChange(new PriceChangeCheck(TODAY, TODAY, MAX_REASON))).doesNotThrowAnyException();
    }

    @Test
    void aScheduledVersionThatHasNotStartedIsCancellableWithAReason() {
        assertThatCode(() -> evaluator.requireCancellablePriceVersion(
                new PriceVersionCancellationCheck(PriceVersionStatus.SCHEDULED, TODAY.plusDays(1), TODAY, "motivo"))).doesNotThrowAnyException();
    }

    @Test
    void aScheduledVersionStartingTodayIsAlreadyEffective() {
        assertThatThrownBy(() -> evaluator.requireCancellablePriceVersion(
                new PriceVersionCancellationCheck(PriceVersionStatus.SCHEDULED, TODAY, TODAY, "motivo")))
                .isInstanceOf(VersionAlreadyEffectiveException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PriceVersionStatus.class, names = "SCHEDULED", mode = EnumSource.Mode.EXCLUDE)
    void onlyAScheduledVersionCanBeCancelled(PriceVersionStatus status) {
        assertThatThrownBy(() -> evaluator.requireCancellablePriceVersion(
                new PriceVersionCancellationCheck(status, TODAY.plusDays(10), TODAY, "motivo")))
                .isInstanceOf(VersionAlreadyEffectiveException.class);
    }

    @Test
    void theCancellationReasonIsRequiredAndBoundedByTheColumnLength() {
        for (String invalid : new String[]{null, "", " ", MAX_REASON + "x"}) {
            assertThatThrownBy(() -> evaluator.requireCancellablePriceVersion(
                    new PriceVersionCancellationCheck(PriceVersionStatus.SCHEDULED, TODAY.plusDays(1), TODAY, invalid)))
                    .isInstanceOf(CancellationReasonRequiredException.class);
        }

        assertThatCode(() -> evaluator.requireCancellablePriceVersion(
                new PriceVersionCancellationCheck(PriceVersionStatus.SCHEDULED, TODAY.plusDays(1), TODAY, MAX_REASON))).doesNotThrowAnyException();
    }

    @Test
    void theMissingReasonIsReportedBeforeTheAlreadyEffectiveViolation() {
        assertThatThrownBy(() -> evaluator.requireCancellablePriceVersion(
                new PriceVersionCancellationCheck(PriceVersionStatus.EFFECTIVE, TODAY, TODAY, null)))
                .isInstanceOf(CancellationReasonRequiredException.class);
    }

    @Test
    void theChangeMomentRunsTheGenericRetroactivePolicyOverPriceChecks() {
        PolicySet<PriceChangeCheck> set = configuration.priceChangePolicySet();

        assertThat(set.moment()).isEqualTo("PRICE_CHANGE");
        assertThat(set.evaluateAllRules(new PriceChangeCheck(TODAY.minusDays(1), TODAY, null)).violations())
                .extracting(violation -> violation.code())
                .containsExactly("CHANGE_REASON_REQUIRED", "RETROACTIVE_EFFECTIVE_DATE");
    }
}
