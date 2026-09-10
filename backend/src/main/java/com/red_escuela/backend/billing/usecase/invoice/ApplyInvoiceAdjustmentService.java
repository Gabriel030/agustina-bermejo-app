package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

// Decisión 2026-09-03: solo el Director otorga becas/descuentos/condonaciones — Administrativo
// ya no puede cargar un DISCOUNT/WAIVE_DEBT directo, solo registrar pagos y emitir dentro de
// las reglas que el Director definió (rol enforced en el controller).
@UseCase
public class ApplyInvoiceAdjustmentService implements ApplyInvoiceAdjustmentUseCase {

    private final InvoiceRepository invoiceRepository;
    private final CurrentPersonService currentPersonService;

    public ApplyInvoiceAdjustmentService(InvoiceRepository invoiceRepository, CurrentPersonService currentPersonService) {
        this.invoiceRepository = invoiceRepository;
        this.currentPersonService = currentPersonService;
    }

    // Mismo lock pesimista que un pago (RegisterPaymentService): dos Administrativos/Directores
    // otorgando un descuento a la misma cuota al mismo tiempo tienen el mismo riesgo de carrera
    // que dos pagos parciales simultáneos.
    @Override
    @Transactional
    public Invoice apply(ApplyInvoiceAdjustmentCommand command) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(command.invoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.WAIVED) {
            throw new InvoiceNotPayableException(command.invoiceId(), invoice.getStatus().name());
        }
        var request = command.request();
        invoice.applyAdjustment(request.type(), request.value().toValue(), request.reason(), currentPersonService.resolveCurrentPerson());
        return invoiceRepository.save(invoice);
    }
}
