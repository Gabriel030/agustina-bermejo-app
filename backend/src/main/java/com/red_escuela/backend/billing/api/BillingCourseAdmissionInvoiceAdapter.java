package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.invoice.service.CourseAdmissionInvoiceIssuer;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoicePort;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoiceView;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class BillingCourseAdmissionInvoiceAdapter implements CourseAdmissionInvoicePort {

    private static final CourseAdmissionInvoiceView NO_INVOICE = new CourseAdmissionInvoiceView(null, false);

    private final CourseAdmissionInvoiceIssuer issuer;
    private final CurrentBillingModePort billingMode;

    public BillingCourseAdmissionInvoiceAdapter(CourseAdmissionInvoiceIssuer issuer, CurrentBillingModePort billingMode) {
        this.issuer = issuer;
        this.billingMode = billingMode;
    }

    @Override
    public CourseAdmissionInvoiceView issueCourseAdmissionInvoice(Long studentId, Long courseId) {
        if (billingMode.currentMode() == BillingMode.OFF) {
            return NO_INVOICE;
        }
        return issuer.issueCourseAdmissionInvoice(studentId, courseId);
    }

    @Override
    public void assignInvoiceToCourseEnrollment(Long invoiceId, Long courseEnrollmentId) {
        if (billingMode.currentMode() == BillingMode.OFF) {
            return;
        }
        issuer.assignInvoiceToCourseEnrollment(invoiceId, courseEnrollmentId);
    }
}
