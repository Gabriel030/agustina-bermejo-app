package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvanceEnrollmentFeeExpiration {
    private static final Logger log = LoggerFactory.getLogger(AdvanceEnrollmentFeeExpiration.class);
    private static final String JUSTIFICATION = "La matrícula del año siguiente pagada por adelantado venció al cerrar la ventana de inscripción sin pagarse";

    private final SystemAdmissionInvoiceCancellation invoiceCancellation;

    public AdvanceEnrollmentFeeExpiration(SystemAdmissionInvoiceCancellation invoiceCancellation) {
        this.invoiceCancellation = invoiceCancellation;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelWhenWindowClosedAndUnpaid(Long invoiceId) {
        Outcome outcome = invoiceCancellation.cancelWhenUnpaid(invoiceId, SystemCancellationOrigin.ADVANCE_ENROLLMENT_FEE_EXPIRED, JUSTIFICATION);
        if (outcome == Outcome.CANCELLED) {
            log.info("Matrícula anticipada vencida: factura={}", invoiceId);
        }
        return outcome == Outcome.CANCELLED;
    }
}
