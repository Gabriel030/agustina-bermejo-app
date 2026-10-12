package com.red_escuela.backend.billing.payment.gateway.manual;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.exception.UnsupportedGatewayOperationException;
import com.red_escuela.backend.billing.payment.gateway.GatewayNotification;
import com.red_escuela.backend.billing.payment.gateway.GatewayPaymentSnapshot;
import com.red_escuela.backend.billing.payment.gateway.GatewayRefundResult;
import com.red_escuela.backend.billing.payment.gateway.OnlineCheckoutRequest;
import com.red_escuela.backend.billing.payment.gateway.OnlineCheckoutSession;
import com.red_escuela.backend.billing.payment.gateway.PaymentGateway;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayCapabilities;
import com.red_escuela.backend.billing.payment.gateway.WebhookHttpRequest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ManualPaymentGateway implements PaymentGateway {

    private static final PaymentGatewayCapabilities CAPABILITIES = new PaymentGatewayCapabilities(
            false, false, false, false, false, Set.of(Currency.ARS, Currency.USD), false, false, false);

    @Override
    public PaymentGatewayCode gatewayCode() {
        return PaymentGatewayCode.MANUAL;
    }

    @Override
    public PaymentGatewayCapabilities describeCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public OnlineCheckoutSession createOnlineCheckout(OnlineCheckoutRequest request) {
        throw new UnsupportedGatewayOperationException(gatewayCode(), "online checkout");
    }

    @Override
    public GatewayNotification parseAndVerifyWebhookNotification(WebhookHttpRequest request) {
        throw new UnsupportedGatewayOperationException(gatewayCode(), "webhook notifications");
    }

    @Override
    public GatewayPaymentSnapshot fetchPaymentFromGateway(String gatewayPaymentId) {
        throw new UnsupportedGatewayOperationException(gatewayCode(), "fetching payments");
    }

    @Override
    public GatewayRefundResult requestRefundToGateway(String gatewayPaymentId, NonNegativeMoney amount) {
        throw new UnsupportedGatewayOperationException(gatewayCode(), "gateway refunds");
    }
}
