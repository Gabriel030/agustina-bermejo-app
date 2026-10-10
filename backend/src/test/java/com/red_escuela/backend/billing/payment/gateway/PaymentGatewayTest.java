package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.RequiredGatewayCapability;
import com.red_escuela.backend.billing.payment.exception.PaymentGatewayCapabilityNotSupportedException;
import com.red_escuela.backend.billing.payment.exception.PaymentGatewayNotFoundException;
import com.red_escuela.backend.billing.payment.exception.UnsupportedGatewayOperationException;
import com.red_escuela.backend.billing.payment.gateway.manual.ManualPaymentGateway;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityPolicy;
import com.red_escuela.backend.billing.gateway.exception.PaymentGatewayUnavailableException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayTest {

    private final PolicySet<PaymentGatewayAvailabilityContext> availability = PolicySet.named("PAYMENT_GATEWAY_RESOLUTION", List.of(new PaymentGatewayAvailabilityPolicy()));
    private final PolicySet<PaymentGatewayCapabilityContext> capability = PolicySet.named("PAYMENT_GATEWAY_CAPABILITY", List.of(new PaymentGatewayCapabilityPolicy()));
    private final ManualPaymentGateway manual = new ManualPaymentGateway();

    private PaymentGatewayRegistry registryOf(PaymentGateway... gateways) {
        return registryWithMpEnabled(true, gateways);
    }

    private PaymentGatewayRegistry registryWithMpEnabled(boolean mpEnabled, PaymentGateway... gateways) {
        PaymentGatewayConfig config = new PaymentGatewayConfig();
        config.setMpEnabled(mpEnabled);
        PaymentGatewayConfigRepository configRepository = Mockito.mock(PaymentGatewayConfigRepository.class);
        Mockito.when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        return new PaymentGatewayRegistry(List.of(gateways), availability, capability, configRepository);
    }

    private PaymentGateway fakeGateway(PaymentGatewayCode code) {
        return new ManualPaymentGateway() {
            @Override
            public PaymentGatewayCode gatewayCode() {
                return code;
            }
        };
    }

    @Test
    @Tag("D-082")
    @Tag("D-097")
    void theManualGatewayDeclaresOnlyArsAndUsdAndNothingElse() {
        PaymentGatewayCapabilities capabilities = manual.describeCapabilities();

        assertThat(manual.gatewayCode()).isEqualTo(PaymentGatewayCode.MANUAL);
        assertThat(capabilities.supportedCurrencies()).containsExactlyInAnyOrder(Currency.ARS, Currency.USD);
        assertThat(List.of(capabilities.supportsOnlineCheckout(), capabilities.supportsFullRefund(), capabilities.supportsPartialRefund(),
                capabilities.notifiesChargebacks(), capabilities.supportsCardInstallments(), capabilities.supportsAutomaticDebit(),
                capabilities.supportsMultiItemCheckout(), capabilities.supportsAuthorizationHold())).containsOnly(false);
    }

    @Test
    @Tag("D-082")
    void theCapabilitiesAreImmutableEvenIfTheCallerKeepsTheCurrencySet() {
        Set<Currency> currencies = new HashSet<>(Set.of(Currency.ARS));
        PaymentGatewayCapabilities capabilities = new PaymentGatewayCapabilities(true, true, true, true, true, currencies, true, true, true);

        currencies.add(Currency.USD);

        assertThat(capabilities.supportedCurrencies()).containsExactly(Currency.ARS);
        assertThatThrownBy(() -> capabilities.supportedCurrencies().add(Currency.USD)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(RequiredGatewayCapability.class)
    @Tag("D-082")
    void everyCapabilityTheManualGatewayDoesNotDeclareIsRejected(RequiredGatewayCapability required) {
        PaymentGatewayRegistry registry = registryOf(manual);

        assertThatThrownBy(() -> registry.findGatewayDeclaring(PaymentGatewayCode.MANUAL, Currency.ARS, Set.of(required)))
                .isInstanceOf(PaymentGatewayCapabilityNotSupportedException.class)
                .satisfies(exception -> assertThat(((PaymentGatewayCapabilityNotSupportedException) exception).violations())
                        .extracting(violation -> violation.code()).containsExactly("PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED"))
                .hasMessageContaining(required.name());
    }

    @Test
    @Tag("D-082")
    @Tag("D-097")
    void aCurrencyOutsideTheDeclaredOnesIsRejectedAndTheDeclaredOnesPass() {
        PaymentGatewayCapabilities arsOnly = new PaymentGatewayCapabilities(false, false, false, false, false, Set.of(Currency.ARS), false, false, false);
        PaymentGatewayCapabilityPolicy policy = new PaymentGatewayCapabilityPolicy();

        assertThat(policy.evaluateRule(new PaymentGatewayCapabilityContext(arsOnly, Currency.USD, Set.of())).code()).isEqualTo("PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED");
        assertThat(policy.evaluateRule(new PaymentGatewayCapabilityContext(arsOnly, Currency.ARS, Set.of())).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PaymentGatewayCapabilityContext(manual.describeCapabilities(), Currency.USD, Set.of())).allowed()).isTrue();
        assertThat(registryOf(manual).findGatewayDeclaring(PaymentGatewayCode.MANUAL, Currency.USD, Set.of())).isSameAs(manual);
        assertThat(policy.ruleName()).isEqualTo("PaymentGatewayCapabilityPolicy");
        assertThat(new PaymentGatewayAvailabilityPolicy().ruleName()).isEqualTo("PaymentGatewayAvailabilityPolicy");
    }

    @Test
    @Tag("D-082")
    void everyDeclaredCapabilityIsAcceptedWhenRequiredTogether() {
        PaymentGatewayCapabilities everything = new PaymentGatewayCapabilities(true, true, true, true, true, Set.of(Currency.ARS), true, true, true);

        assertThat(new PaymentGatewayCapabilityPolicy().evaluateRule(new PaymentGatewayCapabilityContext(everything, Currency.ARS, EnumSet.allOf(RequiredGatewayCapability.class))).allowed()).isTrue();
    }

    @Test
    @Tag("D-082")
    void twoGatewaysWithTheSameCodeMakeTheRegistryFailAtStartup() {
        assertThatThrownBy(() -> registryOf(manual, new ManualPaymentGateway()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANUAL");
    }

    @Test
    @Tag("D-082")
    void aCodeWithoutAnAdapterThatIsNotAnOnlineProviderIsNotFound() {
        PaymentGatewayRegistry registry = registryOf(fakeGateway(PaymentGatewayCode.MERCADO_PAGO));

        assertThatThrownBy(() -> registry.findGatewayByCode(PaymentGatewayCode.MANUAL)).isInstanceOf(PaymentGatewayNotFoundException.class);
        assertThat(registry.findGatewayByCode(PaymentGatewayCode.MERCADO_PAGO).gatewayCode()).isEqualTo(PaymentGatewayCode.MERCADO_PAGO);
    }

    @Test
    @Tag("D-226")
    void anOnlineProviderWithoutAnEnabledAdapterIsUnavailableAndTheManualOneResolves() {
        PaymentGatewayRegistry registry = registryOf(manual);

        assertThatThrownBy(() -> registry.findGatewayByCode(PaymentGatewayCode.MERCADO_PAGO)).isInstanceOf(PaymentGatewayUnavailableException.class)
                .satisfies(exception -> assertThat(((PaymentGatewayUnavailableException) exception).violations())
                        .extracting(violation -> violation.code()).containsExactly("PAYMENT_GATEWAY_UNAVAILABLE"));
        assertThatThrownBy(() -> registry.findGatewayByCode(PaymentGatewayCode.DLOCAL)).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThatThrownBy(() -> registry.findGatewayDeclaring(PaymentGatewayCode.MERCADO_PAGO, Currency.ARS, Set.of(RequiredGatewayCapability.ONLINE_CHECKOUT)))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(registry.findGatewayByCode(PaymentGatewayCode.MANUAL)).isSameAs(manual);
    }

    @Test
    @Tag("D-226")
    void theAvailabilityPolicyOnlyBlocksOnlineProvidersWithoutAnAdapter() {
        PaymentGatewayAvailabilityPolicy policy = new PaymentGatewayAvailabilityPolicy();

        assertThat(policy.evaluateRule(new PaymentGatewayAvailabilityContext(PaymentGatewayCode.MANUAL, false, false)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PaymentGatewayAvailabilityContext(PaymentGatewayCode.MERCADO_PAGO, true, true)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PaymentGatewayAvailabilityContext(PaymentGatewayCode.MERCADO_PAGO, false, true)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new PaymentGatewayAvailabilityContext(PaymentGatewayCode.DLOCAL, false, false)).allowed()).isFalse();
    }

    @Test
    @Tag("D-226")
    void anOnlineProviderWithAnAdapterButDisabledByConfigurationIsUnavailable() {
        PaymentGatewayAvailabilityPolicy policy = new PaymentGatewayAvailabilityPolicy();

        assertThat(policy.evaluateRule(new PaymentGatewayAvailabilityContext(PaymentGatewayCode.MERCADO_PAGO, true, false)).allowed()).isFalse();
        PaymentGatewayRegistry disabled = registryWithMpEnabled(false, fakeGateway(PaymentGatewayCode.MERCADO_PAGO));
        assertThatThrownBy(() -> disabled.findGatewayByCode(PaymentGatewayCode.MERCADO_PAGO)).isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    @Tag("D-082")
    void theManualGatewayRefusesEveryProviderOperation() {
        assertThatThrownBy(() -> manual.createOnlineCheckout(new OnlineCheckoutRequest(1L, null, List.of(1L), "k"))).isInstanceOf(UnsupportedGatewayOperationException.class);
        assertThatThrownBy(() -> manual.parseAndVerifyWebhookNotification(new WebhookHttpRequest(Map.of(), "{}"))).isInstanceOf(UnsupportedGatewayOperationException.class);
        assertThatThrownBy(() -> manual.fetchPaymentFromGateway("1")).isInstanceOf(UnsupportedGatewayOperationException.class);
        assertThatThrownBy(() -> manual.requestRefundToGateway("1", null)).isInstanceOf(UnsupportedGatewayOperationException.class);
    }
}
