package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingPreview;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingPreviewer;
import com.red_escuela.backend.enrollment.api.CourseAdmissionChargePreview;
import com.red_escuela.backend.enrollment.api.CourseAdmissionChargePreviewPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class CourseAdmissionChargePreviewer implements CourseAdmissionChargePreviewPort {

    private final InvoiceIssuingPreviewer previewer;
    private final Clock clock;

    public CourseAdmissionChargePreviewer(InvoiceIssuingPreviewer previewer, Clock clock) {
        this.previewer = previewer;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseAdmissionChargePreview previewCourseAdmissionCharge(Long studentId, Long courseId) {
        InvoiceIssuingPreview preview = previewer.previewInvoiceIssuing(InvoiceIssuingRequestFactory.createCourseAdmissionRequest(studentId, courseId, LocalDate.now(clock)));
        return switch (preview) {
            case InvoiceIssuingPreview.InvoiceWouldBeIssued issued -> CourseAdmissionChargePreview.chargeable(issued.total().value());
            case InvoiceIssuingPreview.InvoiceWouldBeAlreadyIssued duplicated -> CourseAdmissionChargePreview.alreadyCharged(duplicated.invoiceId());
            case InvoiceIssuingPreview.InvoiceWouldBeRejected rejected -> CourseAdmissionChargePreview.rejected(rejected.evaluationResult().describeDenials());
        };
    }
}
