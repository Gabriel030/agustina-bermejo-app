package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.service.CurrentStudentService;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentStudentService currentStudentService;
    private final CreditNoteService creditNoteService;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                           CurrentStudentService currentStudentService, CreditNoteService creditNoteService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentStudentService = currentStudentService;
        this.creditNoteService = creditNoteService;
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
            // Defensa en profundidad, además de la validación @DecimalMin del DTO (que solo
            // protege el camino HTTP): un amountApplied negativo o cero corrompería el saldo en
            // silencio (nunca supera getRemainingBalance(), así que pasaba el chequeo de abajo sin
            // disparar nunca PAID) — hallazgo de auditoría 2026-09-04.
            if (amountApplied.signum() <= 0) {
                throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + invoice.getId() + ")");
            }
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
        // Se persiste ANTES del loop (no solo al final, como antes de CREDIT_BALANCE): una
        // CreditApplication referencia este Payment vía FK, y Hibernate rechaza cascadear una
        // asociación hacia una entidad todavía transient (sin id) apenas la próxima query fuerza
        // un auto-flush — CreditNoteService.consumeForInvoice ya dispara una dentro de este mismo
        // loop.
        payment = paymentRepository.save(payment);

        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            BigDecimal amountApplied = ordered.get(i).amountApplied();

            // Invoice.registerPayment (no invoice.setStatus directo): PaymentAllocation y la
            // transición a PAID son responsabilidad del propio Aggregate Root, no de este service
            // (hallazgo de auditoría 2026-09-04 — la cabecera de Invoice.java ya declaraba esta
            // invariante, este era el único caller que la violaba). No hace falta invoiceRepository
            // .save(invoice) acá: invoice ya es una entidad managed (viene de findByIdForUpdate en
            // ESTA misma transacción), Hibernate flushea el cambio de status solo. La única
            // persistencia explícita de la PaymentAllocation es vía la cascada de payment.save()
            // al final — cascadearla también desde el lado de Invoice duplicaba el INSERT
            // (uk_payment_allocation) porque ambos lados tienen cascade=ALL sobre el mismo objeto.
            PaymentAllocation allocation = invoice.registerPayment(amountApplied, payment);
            payment.getAllocations().add(allocation);
            // CREDIT_BALANCE no aporta plata real: la Invoice se salda igual (arriba), pero acá
            // además se consume el saldo de CreditNote del dueño de esa Invoice puntual — no se
            // asume que todas las invoices de un mismo Payment sean del mismo alumno (podría ser
            // una carrera y un curso, cada uno con su propio saldo).
            if (request.method() == PaymentMethod.CREDIT_BALANCE) {
                creditNoteService.consumeForInvoice(resolveOwnerStudentId(invoice), amountApplied, invoice, payment);
            }
        }

        return payment;
    }

    // Endpoint compartido (spec: "ADMINISTRATIVO|autoservicio alumno (solo sobre invoices
    // propias)") — Optional.empty() de findCurrentStudent() significa que el actor es staff
    // (Administrativo/Director, sin fila Student propia), que puede pagar cualquier Invoice.
    //
    // InvoiceNotFoundException (404), no NotOwnEnrollmentException (403) — corrección 2026-09-04
    // (auditoría): mismo criterio "404-no-403" que ya sigue PaymentReceiptService.validateOwnership
    // para este mismo tipo de chequeo (ownership sobre un recurso Invoice-scoped, a diferencia de
    // NotOwnEnrollmentException, reservada para chequeos enrollment-scoped por id de la URL).
    private void validateOwnershipIfStudent(Invoice invoice) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(resolveOwnerStudentId(invoice))) {
                throw new InvoiceNotFoundException(invoice.getId());
            }
        });
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        return invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
    }
}
