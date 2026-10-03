package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Payment;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id, Money amount, String method, String status, String rejectionReason,
        String registeredByName, Integer cardInstallments, LocalDateTime paidAt,
        LocalDateTime createdAt, LocalDateTime updatedAt, java.util.List<PaymentAllocationResponse> allocations) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getAmount(), payment.getMethod().name(), payment.getStatus().name(),
                payment.getRejectionReason(),
                payment.getRegisteredBy().getName() + " " + payment.getRegisteredBy().getLastname(),
                payment.getCardInstallments(), payment.getPaidAt(), payment.getCreatedAt(), payment.getUpdatedAt(),
                payment.getAllocations().stream().map(PaymentAllocationResponse::from).toList());
    }
}
