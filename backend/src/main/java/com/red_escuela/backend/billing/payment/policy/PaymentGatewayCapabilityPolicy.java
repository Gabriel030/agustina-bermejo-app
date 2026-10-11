package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RequiredGatewayCapability;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayCapabilities;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Arrays;
import java.util.function.Predicate;

@PolicyRule(violationCodes = "PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED", decisions = {"D-082", "D-097"})
public final class PaymentGatewayCapabilityPolicy implements Policy<PaymentGatewayCapabilityContext> {

    private static final String CODE = "PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED";

    @Override
    public String ruleName() {
        return "PaymentGatewayCapabilityPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentGatewayCapabilityContext context) {
        PaymentGatewayCapabilities capabilities = context.capabilities();
        if (!capabilities.supportedCurrencies().contains(context.currency())) {
            return PolicyVerdict.deny(ruleName(), CODE, "El proveedor no opera en la moneda " + context.currency());
        }
        return Arrays.stream(RequiredGatewayCapability.values())
                .filter(context.required()::contains)
                .filter(Predicate.not(capability -> declares(capabilities, capability)))
                .findFirst()
                .map(missing -> PolicyVerdict.deny(ruleName(), CODE, "El proveedor no declara la capacidad " + missing))
                .orElseGet(() -> PolicyVerdict.allow(ruleName()));
    }

    private boolean declares(PaymentGatewayCapabilities capabilities, RequiredGatewayCapability capability) {
        return switch (capability) {
            case ONLINE_CHECKOUT -> capabilities.supportsOnlineCheckout();
            case FULL_REFUND -> capabilities.supportsFullRefund();
            case PARTIAL_REFUND -> capabilities.supportsPartialRefund();
            case CHARGEBACK_NOTIFICATION -> capabilities.notifiesChargebacks();
            case CARD_INSTALLMENTS -> capabilities.supportsCardInstallments();
            case AUTOMATIC_DEBIT -> capabilities.supportsAutomaticDebit();
            case MULTI_ITEM_CHECKOUT -> capabilities.supportsMultiItemCheckout();
            case AUTHORIZATION_HOLD -> capabilities.supportsAuthorizationHold();
        };
    }
}
