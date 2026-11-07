package com.red_escuela.backend.billing.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;

import java.util.List;

public record RefundRequestResponse(@Schema(nullable = true) Long authorizationRequestId, List<RefundResponse> parts) {

    public static RefundRequestResponse from(RefundRequestResult result) {
        return new RefundRequestResponse(result.authorizationRequestId(), result.parts().stream().map(RefundResponse::from).toList());
    }
}
