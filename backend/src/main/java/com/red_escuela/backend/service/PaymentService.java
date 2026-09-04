package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.PaymentAllocationRequest;
import com.red_escuela.backend.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Payment;
import com.red_escuela.backend.entity.PaymentAllocation;
import com.red_escuela.backend.entity.PaymentMethod;
import com.red_escuela.backend.entity.PaymentStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.InvoiceNotPayableException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentStudentService currentStudentService;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                           CurrentStudentService currentStudentService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentStudentService = currentStudentService;
    }

    // registeredBy es explícito, no se resuelve acá vía CurrentPersonService — este método lo
    // llaman tanto un controller (que sí puede resolver "quién está autenticado ahora") como
    // PaymentReceiptService.aprobar (que ya recibe el actor como parámetro, decidido por SU
    // propio caller) — resolverlo acá duplicaría la fuente de verdad y rompería el segundo caso
    // en cualquier llamada que no pase por un request HTTP real (ej. un test de service directo).
    @Transactional
    public Payment registerPayment(RegisterPaymentRequest request, Person registeredBy) {
        BigDecimal total = request.allocations().stream()
                .map(PaymentAllocationRequest::amountApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.rejected()) {
            Payment rejected = new Payment();
            rejected.setAmount(total);
            rejected.setMethod(request.method());
            rejected.setStatus(PaymentStatus.REJECTED);
            rejected.setRejectionReason(request.rejectionReason());
            rejected.setRegisteredBy(registeredBy);
            rejected.setPaidAt(LocalDateTime.now());
            rejected.setCardInstallments(request.cardInstallments());
            rejected.setNotes(request.notes());
            // Rechazado: no crea ninguna PaymentAllocation, no toca ninguna Invoice — un pago
            // que no se concreta no salda saldo a ningún efecto, pero queda registrado para
            // auditoría.
            return paymentRepository.save(rejected);
        }

        // Orden estable (por invoiceId ascendente) para evitar deadlocks entre dos requests que
        // lockean el mismo par de Invoice en orden distinto.
        List<PaymentAllocationRequest> ordered = request.allocations().stream()
                .sorted(Comparator.comparing(PaymentAllocationRequest::invoiceId))
                .toList();

        // Lock pesimista (SELECT ... FOR UPDATE), no solo @Version: dos pagos PARCIALES
        // concurrentes podrían leer el mismo saldo "viejo" y las dos pasar la validación antes
        // de que ninguna commitee — @Version solo protege el caso de saldo completo.
        List<Invoice> lockedInvoices = ordered.stream()
                .map(allocation -> invoiceRepository.findByIdForUpdate(allocation.invoiceId())
                        .orElseThrow(() -> new InvoiceNotFoundException(allocation.invoiceId())))
                .toList();

        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            BigDecimal amountApplied = ordered.get(i).amountApplied();
            validateOwnershipIfStudent(invoice);
            if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.WAIVED) {
                throw new InvoiceNotPayableException(invoice.getId(), invoice.getStatus().name());
            }
            if (amountApplied.compareTo(invoice.getRemainingBalance()) > 0) {
                throw new PaymentExceedsInvoiceBalanceException(invoice.getId(), amountApplied, invoice.getRemainingBalance());
            }
        }

        Payment payment = new Payment();
        payment.setAmount(total);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now());
        payment.setExternalReference(request.externalReference());
        payment.setCardInstallments(request.cardInstallments());
        payment.setNotes(request.notes());

        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            BigDecimal amountApplied = ordered.get(i).amountApplied();

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setInvoice(invoice);
            allocation.setAmountApplied(amountApplied);
            payment.getAllocations().add(allocation);

            if (invoice.getRemainingBalance().subtract(amountApplied).compareTo(BigDecimal.ZERO) == 0) {
                invoice.setStatus(InvoiceStatus.PAID);
                // InvoicePaidEvent se agrega en Fase 6.8 (ARCA) cuando exista un listener real —
                // publicar un evento sin nadie escuchándolo todavía sería código especulativo.
                invoiceRepository.save(invoice);
            }
        }

        return paymentRepository.save(payment);
    }

    // Endpoint compartido (spec: "ADMINISTRATIVO|autoservicio alumno (solo sobre invoices
    // propias)") — Optional.empty() de findCurrentStudent() significa que el actor es staff
    // (Administrativo/Director, sin fila Student propia), que puede pagar cualquier Invoice.
    private void validateOwnershipIfStudent(Invoice invoice) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            Long ownerId = invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                    : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                    : null;
            if (!student.getId().equals(ownerId)) {
                throw new NotOwnEnrollmentException(student.getId(), invoice.getId());
            }
        });
    }
}
