package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("D-052")
class PlannedInvoiceIssuingServiceDestinationTest {

    @Test
    void issuingInTheCallerTransactionRejectsADraftDestinationInsteadOfEmittingDirectly() {
        InvoiceIssuingService issuingService = mock(InvoiceIssuingService.class);
        PlannedInvoiceIssuingService service = new PlannedInvoiceIssuingService(issuingService);

        assertThatThrownBy(() -> service.issuePlannedProgramInvoice(null, null, null, InvoiceIssuingReason.MONTHLY_RUN, true, new InvoiceIssuanceDestination.DraftOfMonthlyRun(5L)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(issuingService);
    }
}
