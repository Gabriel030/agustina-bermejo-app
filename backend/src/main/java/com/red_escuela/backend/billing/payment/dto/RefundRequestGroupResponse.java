package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.RefundRequestGroup;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record RefundRequestGroupResponse(Long refundRequestId, Long authorizationRequestId, LocalDate requestedOn, Money totalAmount, Money completedAmount,
                                         Money pendingAmount, List<RefundResponse> parts) {

    public static RefundRequestGroupResponse from(RefundRequestGroup group) {
        return new RefundRequestGroupResponse(group.refundRequestId(), group.authorizationRequestId(), group.requestedOn(), group.totalAmount(), group.completedAmount(),
                group.pendingAmount(), group.parts().stream().map(RefundResponse::from).toList());
    }
}
