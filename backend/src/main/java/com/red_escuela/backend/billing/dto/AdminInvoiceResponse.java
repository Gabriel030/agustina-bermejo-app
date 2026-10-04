package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record AdminInvoiceResponse(
        Long id, String feeType, String period, LocalDate dueDate, Money baseAmount,
        Money amountDue, Money remainingBalance, String status,
        List<InvoiceAdjustmentResponse> adjustments, String fiscalInvoiceStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        String cancelledByName, String cancelledReason, LocalDateTime cancelledAt,
        Long studentId, String studentDni, ProductRef productRef,
        String description, Long subjectId, Long examBoardRegistrationId,
        LocalDateTime paymentDate, String paymentMethod,
        List<AdminInvoicePaymentResponse> payments, List<AdminInvoiceReceiptResponse> receipts) {

    public static AdminInvoiceResponse of(Invoice invoice, List<PaymentReceipt> receipts) {
        InvoiceResponse base = InvoiceResponse.from(invoice);
        Map<Long, Long> receiptIdByPaymentId = receipts.stream()
                .filter(receipt -> receipt.getPayment() != null)
                .collect(Collectors.toMap(receipt -> receipt.getPayment().getId(), PaymentReceipt::getId, (first, second) -> first));
        List<AdminInvoicePaymentResponse> payments = invoice.getAllocations().stream()
                .filter(allocation -> allocation.getPayment().getStatus() == PaymentStatus.CONFIRMED)
                .sorted(Comparator.comparing((PaymentAllocation a) -> a.getPayment().getPaidAt()).reversed())
                .map(allocation -> AdminInvoicePaymentResponse.from(allocation, receiptIdByPaymentId))
                .toList();
        return new AdminInvoiceResponse(
                base.id(), base.feeType(), base.period(), base.dueDate(), base.baseAmount(),
                base.amountDue(), base.remainingBalance(), base.status(),
                base.adjustments(), base.fiscalInvoiceStatus(),
                base.createdAt(), base.updatedAt(),
                base.cancelledByName(), base.cancelledReason(), base.cancelledAt(),
                base.studentId(), base.studentDni(), base.productRef(),
                base.description(), base.subjectId(), base.examBoardRegistrationId(),
                base.paymentDate(), base.paymentMethod(),
                payments, receipts.stream().map(AdminInvoiceReceiptResponse::from).toList());
    }
}
