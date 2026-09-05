package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Payment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.InvoiceAlreadyCancelledException;
import com.red_escuela.backend.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF-AL-10: registro de pago contra una factura, sin pasarela (decisión 1). El recálculo de
 * Invoice.status corre en la misma transacción que el alta del Payment (decisión 4), mismo
 * criterio que GradeItemService.recalcularEstadoCursada: nunca lo decide el Administrativo a
 * mano tocando el status.
 */
@Service
public class PaymentService {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentPersonService currentPersonService;

    public PaymentService(
            InvoiceService invoiceService, InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository, CurrentPersonService currentPersonService) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Payment registerPayment(Long invoiceId, RegisterPaymentRequest request) {
        Invoice invoice = invoiceService.findOrThrow(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceAlreadyCancelledException(invoiceId);
        }

        BigDecimal currentBalance = invoiceService.calculatePendingBalance(invoice);
        if (request.amount().compareTo(currentBalance) > 0) {
            throw new PaymentExceedsInvoiceBalanceException(invoiceId);
        }

        Person registeredBy = currentPersonService.resolveCurrentPerson();
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setPaidAt(LocalDateTime.now());
        payment.setMethod(request.method());
        payment.setRegisteredBy(registeredBy);
        payment.setNotes(request.notes());
        payment = paymentRepository.save(payment);

        BigDecimal newBalance = currentBalance.subtract(request.amount());
        invoice.setStatus(newBalance.compareTo(BigDecimal.ZERO) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(invoice);

        return payment;
    }
}
