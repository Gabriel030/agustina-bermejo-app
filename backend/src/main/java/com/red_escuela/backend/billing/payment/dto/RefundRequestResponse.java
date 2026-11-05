package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;

import java.util.List;

public record RefundRequestResponse(Long authorizationRequestId, List<RefundResponse> parts) {

    public static RefundRequestResponse from(RefundRequestResult result) {
        return new RefundRequestResponse(result.authorizationRequestId(), result.parts().stream().map(RefundResponse::from).toList());
    }
}
