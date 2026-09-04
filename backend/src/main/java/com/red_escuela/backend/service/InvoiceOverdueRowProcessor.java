package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CollectionsPolicy;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Bean separado de InvoiceOverdueSweepService a propósito: @Transactional(REQUIRES_NEW) no se
// respeta en una auto-invocación dentro de la misma clase (el proxy de Spring no intercepta
// this.metodo()), así que cada fila necesita pasar por un bean distinto para que su propia
// transacción real quede aislada de las demás filas del lote.
@Service
public class InvoiceOverdueRowProcessor {

    private final InvoiceRepository invoiceRepository;

    public InvoiceOverdueRowProcessor(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long invoiceId, CollectionsPolicy policy) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoice.applyAdjustment(InvoiceAdjustmentType.SURCHARGE,
                new MonetaryAdjustmentValue.PercentageAmount(policy.getSurchargePercentage()), "Mora automática", null);
        invoiceRepository.save(invoice);
    }
}
