package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateInvoiceAdjustmentRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.red_escuela.backend.identity.service.CurrentPersonService;

// Decisión 2026-09-03: solo el Director otorga becas/descuentos/condonaciones — Administrativo
// ya no puede cargar un DISCOUNT/WAIVE_DEBT directo, solo registrar pagos (ver PaymentController)
// y emitir dentro de las reglas que el Director definió (rol enforced en el controller).
@Service
public class InvoiceAdjustmentService {

    private final InvoiceRepository invoiceRepository;
    private final CurrentPersonService currentPersonService;

    public InvoiceAdjustmentService(InvoiceRepository invoiceRepository, CurrentPersonService currentPersonService) {
        this.invoiceRepository = invoiceRepository;
        this.currentPersonService = currentPersonService;
    }

    // Mismo lock pesimista que un pago (PaymentService.registerPayment): dos Administrativos/
    // Directores otorgando un descuento a la misma cuota al mismo tiempo tienen el mismo riesgo
    // de carrera que dos pagos parciales simultáneos.
    @Transactional
    public Invoice applyAdjustment(Long invoiceId, CreateInvoiceAdjustmentRequest request) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.WAIVED) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus().name());
        }
        invoice.applyAdjustment(request.type(), request.value().toValue(), request.reason(), currentPersonService.resolveCurrentPerson());
        return invoiceRepository.save(invoice);
    }
}
