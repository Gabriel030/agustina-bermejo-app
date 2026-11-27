package com.red_escuela.backend.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.billing.invoice.service.CourseAdmissionInvoiceIssuer;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoiceView;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BillingCourseAdmissionInvoiceAdapterTest {

    private final CourseAdmissionInvoiceIssuer issuer = mock(CourseAdmissionInvoiceIssuer.class);
    private final CurrentBillingModePort billingMode = mock(CurrentBillingModePort.class);
    private final BillingCourseAdmissionInvoiceAdapter adapter = new BillingCourseAdmissionInvoiceAdapter(issuer, billingMode);

    @Test
    void returnsNeutralValueWhenBillingIsOff() {
        when(billingMode.currentMode()).thenReturn(BillingMode.OFF);

        CourseAdmissionInvoiceView view = adapter.issueCourseAdmissionInvoice(1L, 2L);
        adapter.assignInvoiceToCourseEnrollment(3L, 4L);

        assertThat(view.invoiceId()).isNull();
        assertThat(view.payable()).isFalse();
        verifyNoInteractions(issuer);
    }

    @ParameterizedTest
    @EnumSource(value = BillingMode.class, names = {"MIRROR", "FULL"})
    void delegatesToTheIssuerWhenBillingIsMirrorOrFull(BillingMode mode) {
        when(billingMode.currentMode()).thenReturn(mode);
        CourseAdmissionInvoiceView issued = new CourseAdmissionInvoiceView(9L, true);
        when(issuer.issueCourseAdmissionInvoice(1L, 2L)).thenReturn(issued);

        assertThat(adapter.issueCourseAdmissionInvoice(1L, 2L)).isSameAs(issued);
        adapter.assignInvoiceToCourseEnrollment(9L, 4L);

        verify(issuer).assignInvoiceToCourseEnrollment(9L, 4L);
    }
}
