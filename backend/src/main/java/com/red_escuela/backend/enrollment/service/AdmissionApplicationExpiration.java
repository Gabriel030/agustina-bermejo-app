package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.api.ExpiredAdmissionInvoiceCancellation;
import com.red_escuela.backend.billing.api.ExpiredAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
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

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ExpiredAdmissionInvoiceCancellation invoiceCancellation;
    private final Clock clock;

    public AdmissionApplicationExpiration(ProgramEnrollmentApplicationRepository applicationRepository, ExpiredAdmissionInvoiceCancellation invoiceCancellation, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.invoiceCancellation = invoiceCancellation;
        this.clock = clock;
    }

    @Transactional
    public boolean expireWhenAdmissionClosedAndUnpaid(Long applicationId) {
        LocalDate today = LocalDate.now(clock);
        Optional<Long> invoiceId = applicationRepository.findAdmissionInvoiceIdOfApprovedWithAdmissionClosedBefore(applicationId, today);
        if (invoiceId.isEmpty()) {
            return false;
        }
        Outcome outcome = invoiceCancellation.cancelWhenUnpaid(invoiceId.get());
        if (outcome == Outcome.NOT_CANCELLABLE) {
            return false;
        }
        ProgramEnrollmentApplication application = applicationRepository.findLockedById(applicationId).orElseThrow();
        boolean expired = application.expireIfApproved();
        if (expired) {
            log.info("Solicitud de admisión vencida al cerrar la admisión: solicitud={}, factura={}, resultadoFactura={}", applicationId, invoiceId.get(), outcome);
        }
        return expired;
    }
}
