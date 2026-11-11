package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.service.InvoiceQueryService;

import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentPaymentsStandingPortAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Mock
    private CurrentBillingModePort currentBillingModePort;

    @Mock
    private InvoiceQueryService invoiceQueryService;

    @Test
    void fullBillingSplitsStudentsByOverdueDebt() {
        when(currentBillingModePort.currentMode()).thenReturn(BillingMode.FULL);
        when(invoiceQueryService.findStudentIdsWithOverdueDebt(List.of(1L, 2L), TODAY)).thenReturn(Set.of(2L));

        var standings = new StudentPaymentsStandingPortAdapter(currentBillingModePort, invoiceQueryService)
                .findPaymentsStanding(List.of(1L, 2L), TODAY);

        assertThat(standings).containsEntry(1L, PaymentsStanding.OK).containsEntry(2L, PaymentsStanding.NOT_OK);
    }

    @ParameterizedTest
    @EnumSource(value = BillingMode.class, names = {"OFF", "MIRROR"})
    void nonFullBillingIsNotVerifiedAndNeverReadsInvoices(BillingMode mode) {
        when(currentBillingModePort.currentMode()).thenReturn(mode);

        var standings = new StudentPaymentsStandingPortAdapter(currentBillingModePort, invoiceQueryService)
                .findPaymentsStanding(List.of(1L, 2L), TODAY);

        assertThat(standings).containsEntry(1L, PaymentsStanding.NOT_VERIFIED).containsEntry(2L, PaymentsStanding.NOT_VERIFIED);
        verify(invoiceQueryService, never()).findStudentIdsWithOverdueDebt(List.of(1L, 2L), TODAY);
    }
}
