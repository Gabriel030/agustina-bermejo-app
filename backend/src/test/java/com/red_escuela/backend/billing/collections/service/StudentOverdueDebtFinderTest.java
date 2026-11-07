package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.repository.StudentUnpaidDocumentsQuery;
import com.red_escuela.backend.billing.collections.repository.StudentUnpaidDocumentsQuery.UnprotectedUnpaidDocument;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-288")
@Tag("D-331")
class StudentOverdueDebtFinderTest {

    private static final Long STUDENT_ID = 4L;
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 20);
    private static final LocalDate FLOOR = LocalDate.of(2027, 4, 5);

    private final StudentUnpaidDocumentsQuery query = mock(StudentUnpaidDocumentsQuery.class);
    private final PendingReceiptProtectionWindow protectionWindow = mock(PendingReceiptProtectionWindow.class);
    private final BillingConfigurationReader configurationReader = mock(BillingConfigurationReader.class);
    private StudentOverdueDebtFinder finder;

    @BeforeEach
    void setUp() {
        when(protectionWindow.earliestProtectedUploadDateAsOf(TODAY)).thenReturn(FLOOR);
        finder = new StudentOverdueDebtFinder(query, protectionWindow, configurationReader);
    }

    private void graceOf(LocalDate dueDate, int graceDays) {
        LatePaymentTerms terms = mock(LatePaymentTerms.class);
        when(terms.gracePeriodDays()).thenReturn(graceDays);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(dueDate)).thenReturn(Optional.of(terms));
    }

    private void unpaid(LocalDate dueDate, boolean markedOverdue) {
        when(query.findUnprotectedDueBefore(STUDENT_ID, TODAY, FLOOR)).thenReturn(List.of(new UnprotectedUnpaidDocument(1L, dueDate, markedOverdue)));
    }

    @Test
    void theLastDayOfTheGracePeriodIsNotOverdueYet() {
        LocalDate dueDate = TODAY.minusDays(5);
        graceOf(dueDate, 5);
        unpaid(dueDate, false);

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isFalse();
    }

    @Test
    void theDayAfterTheGracePeriodIsOverdue() {
        LocalDate dueDate = TODAY.minusDays(6);
        graceOf(dueDate, 5);
        unpaid(dueDate, false);

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isTrue();
    }

    @Test
    void theGraceOfTheVersionInEffectOnTheDueDateRules() {
        LocalDate dueDate = TODAY.minusDays(6);
        graceOf(dueDate, 10);
        unpaid(dueDate, false);

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isFalse();
    }

    @Test
    void aDocumentAlreadyMarkedOverdueCountsEvenInsideTheCurrentGrace() {
        LocalDate dueDate = TODAY.minusDays(2);
        graceOf(dueDate, 10);
        unpaid(dueDate, true);

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isTrue();
    }

    @Test
    void withoutAnyLatePaymentVersionTheDayAfterTheDueDateIsOverdue() {
        LocalDate dueDate = TODAY.minusDays(1);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(any())).thenReturn(Optional.empty());
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(Optional.empty());
        unpaid(dueDate, false);

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isTrue();
    }

    @Test
    void withoutUnpaidDocumentsThereIsNoDebt() {
        when(query.findUnprotectedDueBefore(STUDENT_ID, TODAY, FLOOR)).thenReturn(List.of());

        assertThat(finder.hasOverdueDebt(STUDENT_ID, TODAY)).isFalse();
    }
}
