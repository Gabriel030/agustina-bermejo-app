package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.configuration.domain.IssuanceMode;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveMonthlyInvoiceIssuanceModeResolverTest {
    private static final LocalDate FIRST_DAY = LocalDate.of(2043, 3, 1);

    private final BillingConfigurationReader reader = mock(BillingConfigurationReader.class);
    private final EffectiveMonthlyInvoiceIssuanceModeResolver resolver = new EffectiveMonthlyInvoiceIssuanceModeResolver(reader);

    private void configure(IssuanceMode mode) {
        when(reader.findIssuanceSettingsEffectiveOn(FIRST_DAY)).thenReturn(Optional.of(new IssuanceSettings(mode, true, 5, List.of(1, 3))));
    }

    @Test
    @Tag("D-512")
    @Tag("D-519")
    void reviewConfigurationMakesTheNewRunLeaveDrafts() {
        configure(IssuanceMode.REVIEW);

        assertThat(resolver.resolveModeEffectiveOn(FIRST_DAY)).isEqualTo(MonthlyInvoiceIssuanceMode.REVIEW);
    }

    @Test
    @Tag("D-512")
    void directConfigurationMakesTheNewRunEmitRightAway() {
        configure(IssuanceMode.DIRECT);

        assertThat(resolver.resolveModeEffectiveOn(FIRST_DAY)).isEqualTo(MonthlyInvoiceIssuanceMode.DIRECT);
    }

    @Test
    @Tag("D-512")
    void withoutAnEffectiveConfigurationTheRunFailsClosedToReview() {
        when(reader.findIssuanceSettingsEffectiveOn(FIRST_DAY)).thenReturn(Optional.empty());

        assertThat(resolver.resolveModeEffectiveOn(FIRST_DAY)).isEqualTo(MonthlyInvoiceIssuanceMode.REVIEW);
    }
}
