package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.RequiredGatewayCapability;
import com.red_escuela.backend.billing.payment.exception.PaymentGatewayCapabilityNotSupportedException;
import com.red_escuela.backend.billing.payment.exception.PaymentGatewayNotFoundException;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityContext;
import com.red_escuela.backend.billing.gateway.exception.PaymentGatewayUnavailableException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentGatewayCode, PaymentGateway> gatewaysByCode = new EnumMap<>(PaymentGatewayCode.class);
    private final PolicySet<PaymentGatewayAvailabilityContext> availabilityPolicySet;
    private final PolicySet<PaymentGatewayCapabilityContext> capabilityPolicySet;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways, PolicySet<PaymentGatewayAvailabilityContext> paymentGatewayAvailabilityPolicySet,
                                  PolicySet<PaymentGatewayCapabilityContext> paymentGatewayCapabilityPolicySet) {
        for (PaymentGateway gateway : gateways) {
            if (gatewaysByCode.putIfAbsent(gateway.gatewayCode(), gateway) != null) {
                throw new IllegalStateException("Payment gateway declared twice for " + gateway.gatewayCode());
            }
        }
        this.availabilityPolicySet = paymentGatewayAvailabilityPolicySet;
        this.capabilityPolicySet = paymentGatewayCapabilityPolicySet;
    }

    public PaymentGateway findGatewayByCode(PaymentGatewayCode code) {
        availabilityPolicySet.evaluateRulesUntilFirstViolation(new PaymentGatewayAvailabilityContext(code, gatewaysByCode.containsKey(code)))
                .throwIfDenied(PaymentGatewayUnavailableException::new);
        PaymentGateway gateway = gatewaysByCode.get(code);
        if (gateway == null) {
            throw new PaymentGatewayNotFoundException(code);
        }
        return gateway;
    }

    public PaymentGateway findGatewayDeclaring(PaymentGatewayCode code, Currency currency, Set<RequiredGatewayCapability> required) {
        PaymentGateway gateway = findGatewayByCode(code);
        capabilityPolicySet.evaluateRulesUntilFirstViolation(new PaymentGatewayCapabilityContext(gateway.describeCapabilities(), currency, required))
                .throwIfDenied(PaymentGatewayCapabilityNotSupportedException::new);
        return gateway;
    }
}
