package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.dto.InvoiceLineResponse;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MeInvoiceResponse(
        Long id, String invoiceNumber, String invoiceType, String documentType, String documentStatus,
        String paymentStatus, LocalDate issueDate, LocalDate dueDate, String currency,
        Money linesTotalAmount, Money discountsTotalAmount, Money totalAmount, Money paidAmount,
        Money creditedAmount, Money outstandingAmount, List<InvoiceLineResponse> lines,
        String issuingReason, String discardedReason,
        Long studentId, String studentDni, ProductRef productRef,
        String description, Long subjectId, Long examBoardRegistrationId,
        LocalDateTime paymentDate, String paymentMethod,
        List<MeInvoicePaymentResponse> payments, List<MeInvoiceReceiptResponse> receipts) {

    public static MeInvoiceResponse of(InvoiceView view, List<BankTransferReceipt> receipts) {
        InvoiceResponse base = InvoiceResponse.from(view);
        Map<Long, Long> receiptIdByPaymentId = receipts.stream()
                .filter(receipt -> receipt.getPaymentId() != null)
                .collect(Collectors.toMap(receipt -> receipt.getPaymentId(), BankTransferReceipt::getId, (first, second) -> first));
        List<MeInvoicePaymentResponse> payments = view.paymentShares().stream()
                .filter(InvoicePaymentShare::isConfirmed)
                .sorted(InvoicePaymentShare.CHRONOLOGICAL.reversed())
                .map(share -> MeInvoicePaymentResponse.from(share, receiptIdByPaymentId))
                .toList();
        return new MeInvoiceResponse(
                base.id(), base.invoiceNumber(), base.invoiceType(), base.documentType(), base.documentStatus(),
                base.paymentStatus(), base.issueDate(), base.dueDate(), base.currency(),
                base.linesTotalAmount(), base.discountsTotalAmount(), base.totalAmount(), base.paidAmount(),
                base.creditedAmount(), base.outstandingAmount(), base.lines(),
                base.issuingReason(), base.discardedReason(),
                base.studentId(), base.studentDni(), base.productRef(),
                base.description(), base.subjectId(), base.examBoardRegistrationId(),
                base.paymentDate(), base.paymentMethod(),
                payments, receipts.stream().map(MeInvoiceReceiptResponse::from).toList());
    }
}
