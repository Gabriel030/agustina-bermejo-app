package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public interface PaymentGateway {

    PaymentGatewayCode gatewayCode();

    PaymentGatewayCapabilities describeCapabilities();

    OnlineCheckoutSession createOnlineCheckout(OnlineCheckoutRequest request);

    GatewayNotification parseAndVerifyWebhookNotification(WebhookHttpRequest request);

    GatewayPaymentSnapshot fetchPaymentFromGateway(String gatewayPaymentId);

    GatewayRefundResult requestRefundToGateway(String gatewayPaymentId, NonNegativeMoney amount);
}
