package com.red_escuela.backend.billing.payment.gateway;

import java.util.Map;

public record WebhookHttpRequest(Map<String, String> headers, String body) {

    public WebhookHttpRequest {
        headers = Map.copyOf(headers);
    }
}
