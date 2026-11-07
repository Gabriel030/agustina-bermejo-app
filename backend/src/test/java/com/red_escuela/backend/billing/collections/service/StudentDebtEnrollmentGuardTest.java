package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.collections.CollectionsPolicySetsConfiguration;
import com.red_escuela.backend.billing.invoice.InvoicePolicySetsConfiguration;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtFacts;
import com.red_escuela.backend.billing.invoice.service.WithdrawnEnrollmentDebtFactsLoader;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-065")
@Tag("D-292")
class StudentDebtEnrollmentGuardTest {

    private static final Long STUDENT_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 12);

    private final WithdrawnEnrollmentDebtFactsLoader withdrawnDebtFacts = mock(WithdrawnEnrollmentDebtFactsLoader.class);
    private final InvoiceFollowUpQueries invoiceFollowUp = mock(InvoiceFollowUpQueries.class);
    private final BillingConfigurationReader configurationReader = mock(BillingConfigurationReader.class);
    private BillingMode mode = BillingMode.FULL;
    private StudentDebtEnrollmentGuard guard;

    @BeforeEach
    void setUp() {
        when(withdrawnDebtFacts.loadFactsOf(any())).thenReturn(new OutstandingDebtFacts(STUDENT_ID, false, false));
        InvoicePolicySetsConfiguration policies = new InvoicePolicySetsConfiguration();
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        guard = new StudentDebtEnrollmentGuard(withdrawnDebtFacts, invoiceFollowUp, configurationReader, () -> mode,
                policies.reenrollmentDebtPolicySet(), new CollectionsPolicySetsConfiguration().newEnrollmentDebtPolicySet(), clock);
    }

    private void blockingRuleIs(boolean enabled) {
        LatePaymentTerms terms = mock(LatePaymentTerms.class);
        when(terms.blockNewEnrollmentsWhenOverdue()).thenReturn(enabled);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(TODAY)).thenReturn(Optional.of(terms));
    }

    private void studentHasOverdueDebt() {
        when(invoiceFollowUp.hasUnpaidIssuedDocumentOverdueOfStudent(STUDENT_ID, TODAY)).thenReturn(true);
    }

    @Test
    void overdueDebtBlocksWhenTheRuleIsEnabled() {
        blockingRuleIs(true);
        studentHasOverdueDebt();

        assertThatThrownBy(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).isInstanceOf(OverdueDebtBlocksNewEnrollmentException.class);
    }

    @Test
    void overdueDebtDoesNotBlockWhenTheRuleIsDisabled() {
        blockingRuleIs(false);
        studentHasOverdueDebt();

        assertThatCode(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void withoutAnyEffectiveConfigurationTheRuleBlocksLikeTheMigratedDefault() {
        when(configurationReader.findLatePaymentSettingsEffectiveOn(any())).thenReturn(Optional.empty());
        studentHasOverdueDebt();

        assertThatThrownBy(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).isInstanceOf(OverdueDebtBlocksNewEnrollmentException.class);
    }

    @Test
    void theDebtOfAWithdrawnEnrollmentBlocksEvenWhenTheOverdueRuleIsDisabled() {
        blockingRuleIs(false);
        when(withdrawnDebtFacts.loadFactsOf(STUDENT_ID)).thenReturn(new OutstandingDebtFacts(STUDENT_ID, true, false));

        assertThatThrownBy(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    @Test
    void theWithdrawnReasonWinsWhenBothDebtsExist() {
        blockingRuleIs(true);
        studentHasOverdueDebt();
        when(withdrawnDebtFacts.loadFactsOf(STUDENT_ID)).thenReturn(new OutstandingDebtFacts(STUDENT_ID, true, false));

        assertThatThrownBy(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    @Test
    void theDisabledRuleSkipsTheOverdueDebtLookup() {
        blockingRuleIs(false);

        guard.verifyNoOutstandingDebt(STUDENT_ID);

        org.mockito.Mockito.verify(invoiceFollowUp, org.mockito.Mockito.never()).hasUnpaidIssuedDocumentOverdueOfStudent(eq(STUDENT_ID), any());
    }

    @Test
    void nothingIsCheckedWhileBillingIsOff() {
        mode = BillingMode.OFF;

        assertThatCode(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).doesNotThrowAnyException();
        verifyNoInteractions(withdrawnDebtFacts, invoiceFollowUp, configurationReader);
    }

    @ParameterizedTest
    @EnumSource(value = BillingMode.class, names = {"MIRROR", "FULL"})
    void debtIsCheckedWhileBillingMirrorsOrIsFull(BillingMode enabledMode) {
        mode = enabledMode;
        blockingRuleIs(true);
        studentHasOverdueDebt();

        assertThatThrownBy(() -> guard.verifyNoOutstandingDebt(STUDENT_ID)).isInstanceOf(OverdueDebtBlocksNewEnrollmentException.class);
    }
}
