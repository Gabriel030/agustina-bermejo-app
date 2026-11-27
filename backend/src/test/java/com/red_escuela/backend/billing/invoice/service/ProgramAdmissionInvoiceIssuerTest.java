package com.red_escuela.backend.billing.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.service.ProgramYearBillingTemplateService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ProgramAdmissionInvoiceIssuerTest {

    private final InvoiceIssuingService issuingService = mock(InvoiceIssuingService.class);
    private final BillingConfigurationReader configurationReader = mock(BillingConfigurationReader.class);
    private final ProgramYearBillingTemplateService templateService = mock(ProgramYearBillingTemplateService.class);
    private final ProgramAdmissionInvoiceIssuer issuer = new ProgramAdmissionInvoiceIssuer(issuingService, configurationReader, templateService, Clock.systemUTC());

    @Test
    void cohortWithoutTemplateIssuesNoAdmissionInvoice() {
        ProgramCohort cohort = mock(ProgramCohort.class);
        when(cohort.getTemplateId()).thenReturn(null);

        assertThat(issuer.issueAdmissionInvoice(1L, mock(Program.class), cohort)).isNull();

        verifyNoInteractions(issuingService, configurationReader, templateService);
    }
}
