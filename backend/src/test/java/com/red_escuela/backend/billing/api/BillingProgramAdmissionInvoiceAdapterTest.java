package com.red_escuela.backend.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BillingProgramAdmissionInvoiceAdapterTest {

    private final ProgramAdmissionInvoiceIssuer issuer = mock(ProgramAdmissionInvoiceIssuer.class);
    private final CurrentBillingModePort billingMode = mock(CurrentBillingModePort.class);
    private final BillingProgramAdmissionInvoiceAdapter adapter = new BillingProgramAdmissionInvoiceAdapter(issuer, billingMode);
    private final Program program = mock(Program.class);
    private final ProgramCohort cohort = mock(ProgramCohort.class);

    @ParameterizedTest
    @EnumSource(value = BillingMode.class, names = {"OFF", "MIRROR"})
    void returnsNoInvoiceWhenBillingIsNotFull(BillingMode mode) {
        when(billingMode.currentMode()).thenReturn(mode);

        assertThat(adapter.issueAdmissionInvoice(1L, program, cohort)).isEmpty();
        verifyNoInteractions(issuer);
    }

    @Test
    void delegatesToTheIssuerWhenBillingIsFull() {
        when(billingMode.currentMode()).thenReturn(BillingMode.FULL);
        Invoice invoice = mock(Invoice.class);
        when(issuer.issueAdmissionInvoice(1L, program, cohort)).thenReturn(invoice);

        assertThat(adapter.issueAdmissionInvoice(1L, program, cohort)).containsSame(invoice);
    }
}
