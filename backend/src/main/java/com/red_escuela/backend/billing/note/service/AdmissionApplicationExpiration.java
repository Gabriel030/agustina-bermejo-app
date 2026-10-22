package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.enrollment.api.AdmissionApplicationExpiryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class AdmissionApplicationExpiration {
    private static final Logger log = LoggerFactory.getLogger(AdmissionApplicationExpiration.class);

    private final AdmissionApplicationExpiryPort applications;
    private final SystemAdmissionInvoiceCancellation invoiceCancellation;
    private final Clock clock;

    public AdmissionApplicationExpiration(AdmissionApplicationExpiryPort applications, SystemAdmissionInvoiceCancellation invoiceCancellation, Clock clock) {
        this.applications = applications;
        this.invoiceCancellation = invoiceCancellation;
        this.clock = clock;
    }

    @Transactional
    public boolean expireWhenAdmissionClosedAndUnpaid(Long applicationId) {
        LocalDate today = LocalDate.now(clock);
        Optional<Long> invoiceId = applications.findAdmissionInvoiceIdOfApprovedApplicationWithAdmissionClosedBefore(applicationId, today);
        if (invoiceId.isEmpty()) {
            return false;
        }
        Outcome outcome = invoiceCancellation.cancelWhenUnpaid(invoiceId.get());
        if (outcome == Outcome.NOT_CANCELLABLE) {
            return false;
        }
        boolean expired = applications.expireIfApproved(applicationId);
        if (!expired && outcome == Outcome.CANCELLED) {
            throw new IllegalStateException("The admission invoice was cancelled but the application " + applicationId + " could not be expired");
        }
        if (expired) {
            log.info("Solicitud de admisión vencida al cerrar la admisión: solicitud={}, factura={}, resultadoFactura={}", applicationId, invoiceId.get(), outcome);
        }
        return expired;
    }
}
