package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.MonthCloseFacts;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery.InstallmentDueDate;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-288")
@Tag("D-331")
class EnrollmentMonthCloseFactsLoaderTest {

    private static final Long ENROLLMENT_ID = 8L;
    private static final LocalDate CUTOFF = LocalDate.of(2027, 4, 30);
    private static final LocalDate FLOOR = LocalDate.of(2027, 4, 5);
    private static final LocalDate DUE = LocalDate.of(2027, 4, 20);

    private final EnrollmentDebtFactsQuery factsQuery = mock(EnrollmentDebtFactsQuery.class);
    private final BillingConfigurationReader configurationReader = mock(BillingConfigurationReader.class);
    private final PendingReceiptProtectionWindow protectionWindow = mock(PendingReceiptProtectionWindow.class);
    private EnrollmentMonthCloseFactsLoader loader;

    @BeforeEach
    void setUp() {
        when(protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF)).thenReturn(FLOOR);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(DUE)).thenReturn(Optional.empty());
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(Optional.empty());
        loader = new EnrollmentMonthCloseFactsLoader(factsQuery, configurationReader, protectionWindow);
    }

    private Optional<LatePaymentTerms> termsWithGrace(int graceDays) {
        LatePaymentTerms terms = mock(LatePaymentTerms.class);
        when(terms.gracePeriodDays()).thenReturn(graceDays);
        return Optional.of(terms);
    }

    private void unpaidDueOn(LocalDate dueDate) {
        when(factsQuery.findMonthlyInstallmentsUnpaidAndUnprotectedAtCutoff(EnrollmentKind.PROGRAM, ENROLLMENT_ID, CUTOFF, FLOOR)).thenReturn(List.of(new InstallmentDueDate(1L, dueDate)));
    }

    @Test
    void dueDateBeforeTheFirstVersionUsesTheFirstVersionGrace() {
        unpaidDueOn(DUE);
        Optional<LatePaymentTerms> grace10 = termsWithGrace(10);
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(grace10);

        MonthCloseFacts facts = loader.loadFactsAtCutoff(EnrollmentKind.PROGRAM, ENROLLMENT_ID, CUTOFF);

        assertThat(facts.overdueInstallmentsAtCutoff()).isZero();
    }

    @Test
    void dueDateBeforeTheFirstVersionWithShortGraceCountsAsOverdue() {
        unpaidDueOn(DUE);
        Optional<LatePaymentTerms> grace3 = termsWithGrace(3);
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(grace3);

        MonthCloseFacts facts = loader.loadFactsAtCutoff(EnrollmentKind.PROGRAM, ENROLLMENT_ID, CUTOFF);

        assertThat(facts.overdueInstallmentsAtCutoff()).isEqualTo(1);
        assertThat(facts.oldestOverdueInstallmentDueDate()).isEqualTo(DUE);
    }

    @Test
    void versionInEffectOnTheDueDateWinsOverTheFirstVersion() {
        unpaidDueOn(DUE);
        Optional<LatePaymentTerms> grace3 = termsWithGrace(3);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(DUE)).thenReturn(grace3);
        Optional<LatePaymentTerms> grace30 = termsWithGrace(30);
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(grace30);

        MonthCloseFacts facts = loader.loadFactsAtCutoff(EnrollmentKind.PROGRAM, ENROLLMENT_ID, CUTOFF);

        assertThat(facts.overdueInstallmentsAtCutoff()).isEqualTo(1);
    }

    @Test
    void withoutAnyVersionTheGraceIsZero() {
        unpaidDueOn(CUTOFF.minusDays(1));
        when(configurationReader.findLatePaymentSettingsEffectiveOn(CUTOFF.minusDays(1))).thenReturn(Optional.empty());

        MonthCloseFacts facts = loader.loadFactsAtCutoff(EnrollmentKind.PROGRAM, ENROLLMENT_ID, CUTOFF);

        assertThat(facts.overdueInstallmentsAtCutoff()).isEqualTo(1);
    }
}
