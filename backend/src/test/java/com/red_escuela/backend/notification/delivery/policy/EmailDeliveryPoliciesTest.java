package com.red_escuela.backend.notification.delivery.policy;

import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("c02")
@Tag("c03")
class EmailDeliveryPoliciesTest {
    private final PreDueEmailOptOutPolicy optOut = new PreDueEmailOptOutPolicy();
    private final VerifiedEmailRequiredPolicy verified = new VerifiedEmailRequiredPolicy();

    @Test
    @Tag("D-340")
    @Tag("D-372")
    void aPreDueMailIsSuppressedWhenTheStudentTurnedThemOff() {
        PolicyVerdict verdict = optOut.evaluateRule(new EmailDeliveryContext(true, false, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("OPTED_OUT");
        assertThat(verdict.policy()).isEqualTo("PreDueEmailOptOutPolicy");
    }

    @Test
    @Tag("D-340")
    @Tag("D-372")
    void aMailAfterTheDueDateCannotBeTurnedOff() {
        assertThat(optOut.evaluateRule(new EmailDeliveryContext(false, false, true)).allowed()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,true", "false,false"})
    @Tag("D-340")
    void aMailIsAllowedUnlessItIsPreDueAndTheStudentTurnedThemOff(boolean precedesDueDate, boolean enabled) {
        assertThat(optOut.evaluateRule(new EmailDeliveryContext(precedesDueDate, enabled, true)).allowed()).isTrue();
    }

    @Test
    @Tag("D-354")
    void aMailToAnUnverifiedAddressIsSuppressed() {
        PolicyVerdict verdict = verified.evaluateRule(new EmailDeliveryContext(false, true, false));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("NO_VERIFIED_EMAIL");
        assertThat(verdict.policy()).isEqualTo("VerifiedEmailRequiredPolicy");
    }

    @Test
    @Tag("D-354")
    void aMailToAVerifiedAddressIsAllowed() {
        assertThat(verified.evaluateRule(new EmailDeliveryContext(false, true, true)).allowed()).isTrue();
    }

    @Test
    @Tag("D-340")
    @Tag("D-354")
    void whenBothRulesDenyTheOptOutIsTheReportedReason() {
        PolicySet<EmailDeliveryContext> set = PolicySet.named("EMAIL_DELIVERY", List.of(optOut, verified));

        var result = set.evaluateRulesUntilFirstViolation(new EmailDeliveryContext(true, false, false));

        assertThat(result.firstDenial()).hasValueSatisfying(denial -> assertThat(denial.code()).isEqualTo("OPTED_OUT"));
    }
}
