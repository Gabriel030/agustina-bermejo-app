package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RegisterPaymentRequest(
        @Valid @NotEmpty List<PaymentAllocationRequest> allocations,
        @NotNull PaymentMethod method,
        boolean rejected,
        String rejectionReason,
        String externalReference,
        Integer cardInstallments,
        String notes,
        String cardType) {

    public RegisterPaymentRequest(List<PaymentAllocationRequest> allocations, PaymentMethod method, boolean rejected, String rejectionReason,
                                  String externalReference, Integer cardInstallments, String notes) {
        this(allocations, method, rejected, rejectionReason, externalReference, cardInstallments, notes, null);
    }
}
