package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MeInvoiceResponse(
        Long id, String feeType, String period, LocalDate dueDate, Money baseAmount,
        Money amountDue, Money remainingBalance, String status,
        List<InvoiceAdjustmentResponse> adjustments, String fiscalInvoiceStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        String cancelledByName, String cancelledReason, LocalDateTime cancelledAt,
        Long studentId, String studentDni, ProductRef productRef,
        String description, Long subjectId, Long examBoardRegistrationId,
        LocalDateTime paymentDate, String paymentMethod,
        List<MeInvoicePaymentResponse> payments, List<MeInvoiceReceiptResponse> receipts) {

    public static MeInvoiceResponse of(Invoice invoice, List<BankTransferReceipt> receipts) {
        InvoiceResponse base = InvoiceResponse.from(invoice);
        Map<Long, Long> receiptIdByPaymentId = receipts.stream()
                .filter(receipt -> receipt.getPayment() != null)
                .collect(Collectors.toMap(receipt -> receipt.getPayment().getId(), BankTransferReceipt::getId, (first, second) -> first));
        List<MeInvoicePaymentResponse> payments = invoice.getAllocations().stream()
                .filter(allocation -> allocation.getPayment().getStatus() == PaymentStatus.CONFIRMED)
                .sorted(Comparator.comparing((PaymentAllocation a) -> a.getPayment().getPaidAt()).reversed())
                .map(allocation -> MeInvoicePaymentResponse.from(allocation, receiptIdByPaymentId))
                .toList();
        return new MeInvoiceResponse(
                base.id(), base.feeType(), base.period(), base.dueDate(), base.baseAmount(),
                base.amountDue(), base.remainingBalance(), base.status(),
                base.adjustments(), base.fiscalInvoiceStatus(),
                base.createdAt(), base.updatedAt(),
                base.cancelledByName(), base.cancelledReason(), base.cancelledAt(),
                base.studentId(), base.studentDni(), base.productRef(),
                base.description(), base.subjectId(), base.examBoardRegistrationId(),
                base.paymentDate(), base.paymentMethod(),
                payments, receipts.stream().map(MeInvoiceReceiptResponse::from).toList());
    }
}
