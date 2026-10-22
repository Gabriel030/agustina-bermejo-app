package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentApplicationCancellationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Component
public class RejectedCourseReceiptCancellation {
    private static final Logger log = LoggerFactory.getLogger(RejectedCourseReceiptCancellation.class);
    private static final String JUSTIFICATION = "El comprobante del curso fue rechazado: se anula la factura y se cancela la solicitud";

    private final CourseEnrollmentApplicationCancellationPort applications;
    private final SystemAdmissionInvoiceCancellation invoiceCancellation;

    public RejectedCourseReceiptCancellation(CourseEnrollmentApplicationCancellationPort applications, SystemAdmissionInvoiceCancellation invoiceCancellation) {
        this.applications = applications;
        this.invoiceCancellation = invoiceCancellation;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> cancelCourseAdmissionsOfRejectedReceipt(Collection<Long> receiptItemInvoiceIds) {
        return receiptItemInvoiceIds.stream().distinct().sorted().filter(this::cancelWhenPendingAndUnpaid).toList();
    }

    private boolean cancelWhenPendingAndUnpaid(Long invoiceId) {
        if (!applications.hasPendingApplicationOfInvoice(invoiceId)) {
            return false;
        }
        Outcome outcome = invoiceCancellation.cancelWhenUnpaid(invoiceId, SystemCancellationOrigin.REJECTED_COURSE_RECEIPT, JUSTIFICATION);
        if (outcome == Outcome.NOT_CANCELLABLE) {
            log.info("Factura de curso con comprobante rechazado que no se puede anular: factura={}", invoiceId);
            return false;
        }
        boolean cancelled = applications.cancelPendingApplicationOfInvoice(invoiceId);
        log.warn("Solicitud de curso cancelada por comprobante rechazado: factura={}, resultadoFactura={}, solicitudCancelada={}", invoiceId, outcome, cancelled);
        return cancelled;
    }
}
