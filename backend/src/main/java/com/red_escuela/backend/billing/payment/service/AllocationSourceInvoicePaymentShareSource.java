package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.service.AllocationSourceQueryService;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShareSource;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShares;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.exception.PaymentSourceIntegrityException;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AllocationSourceInvoicePaymentShareSource implements InvoicePaymentShareSource {

    private final AllocationSourceQueryService allocationSources;
    private final PaymentRepository payments;
    private final PersonNameFinder personNames;

    public AllocationSourceInvoicePaymentShareSource(AllocationSourceQueryService allocationSources, PaymentRepository payments, PersonNameFinder personNames) {
        this.allocationSources = allocationSources;
        this.payments = payments;
        this.personNames = personNames;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoicePaymentShares findCurrentSharesByInvoiceId(Collection<Long> invoiceIds) {
        Map<Long, List<PaymentAllocationSource>> sourcesByInvoice = allocationSources.findCurrentSourcesByInvoiceId(invoiceIds);
        Set<Long> paymentIds = sourcesByInvoice.values().stream().flatMap(List::stream)
                .map(PaymentAllocationSource::getPaymentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (paymentIds.isEmpty()) {
            return new InvoicePaymentShares(Map.of());
        }
        Map<Long, Payment> paymentsById = payments.findByIdIn(paymentIds).stream().collect(Collectors.toMap(Payment::getId, Function.identity()));
        Map<Long, String> recorderNames = personNames.findFullNamesByPersonId(paymentsById.values().stream()
                .map(Payment::getRegisteredByPersonId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, List<InvoicePaymentShare>> resolved = new LinkedHashMap<>();
        sourcesByInvoice.forEach((invoiceId, sources) -> {
            Map<Long, Money> appliedByAccountPayment = new LinkedHashMap<>();
            for (PaymentAllocationSource source : sources) {
                Long paymentId = source.getPaymentId();
                if (paymentId == null) {
                    continue;
                }
                Payment payment = paymentsById.get(paymentId);
                if (payment == null) {
                    throw PaymentSourceIntegrityException.missing(paymentId);
                }
                if (payment.getStatus() == PaymentStatus.APPROVED && payment.getStudentCurrentAccountId().equals(source.getStudentCurrentAccountId())) {
                    appliedByAccountPayment.merge(paymentId, source.getAmount().value(), Money::plus);
                }
            }
            List<InvoicePaymentShare> shares = appliedByAccountPayment.entrySet().stream()
                    .map(entry -> shareOf(paymentsById.get(entry.getKey()), entry.getValue(), recorderNames)).toList();
            if (!shares.isEmpty()) {
                resolved.put(invoiceId, shares);
            }
        });
        return new InvoicePaymentShares(resolved);
    }

    private static InvoicePaymentShare shareOf(Payment payment, Money applied, Map<Long, String> recorderNames) {
        CardPaymentDetails card = payment.getMethodDetails() instanceof CardPaymentDetails details ? details : null;
        String recorder = payment.getRegisteredByPersonId() == null ? payment.getRegisteredByProcess() : recorderNames.get(payment.getRegisteredByPersonId());
        return new InvoicePaymentShare(payment.getId(), InvoicePaymentShare.CONFIRMED, methodLabelOf(payment),
                card == null || card.cardType() == null ? null : card.cardType().name(), card == null ? null : card.installments(),
                applied, (payment.getApprovedAmount() == null ? payment.getAmount() : payment.getApprovedAmount()).value(), paidAtOf(payment),
                externalReferenceOf(payment), payment.getReviewNote(), recorder);
    }

    static String methodLabelOf(Payment payment) {
        if (payment.getGateway() == PaymentGatewayCode.MERCADO_PAGO) {
            return "MERCADO_PAGO";
        }
        return switch (payment.getMethodType()) {
            case BANK_TRANSFER -> "TRANSFER";
            case CASH, CARD, WALLET -> payment.getMethodType().name();
        };
    }

    private static LocalDateTime paidAtOf(Payment payment) {
        return payment.getPaidOn() == null ? payment.getRegisteredAt() : payment.getPaidOn().atStartOfDay();
    }

    private static String externalReferenceOf(Payment payment) {
        if (payment.getGatewayPaymentId() != null) {
            return payment.getGatewayPaymentId();
        }
        return switch (payment.getMethodDetails()) {
            case BankTransferPaymentDetails transfer -> transfer.operationNumber();
            case CashPaymentDetails cash -> cash.receiptNumber();
            default -> null;
        };
    }
}
