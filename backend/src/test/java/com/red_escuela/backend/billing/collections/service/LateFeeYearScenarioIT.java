package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Walks every day of a year with the real late fee reconciliation against Postgres")
class LateFeeYearScenarioIT extends AbstractLateFeeScenarioIT {

    private void runYear(int year, List<Invoice> origins, List<LocalDate> paymentDates) {
        runDays(d(year, 3, 1), d(year, 12, 31), origins, paymentDates);
    }

    @Test
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-332")
    @Tag("D-293")
    void temporalYearOfDailyLateFees() {
        arrange(2253);
        publishDefaults(2253);
        List<Invoice> origins = List.of(overdueInstallmentDueOn(d(2253, 3, 11)), overdueInstallmentDueOn(d(2253, 3, 11)), overdueInstallmentDueOn(d(2253, 3, 11)),
                overdueInstallmentDueOn(d(2253, 3, 11)), overdueInstallmentDueOn(d(2253, 3, 11)));

        runYear(2253, origins, List.of(d(2253, 3, 11), d(2253, 3, 20), d(2253, 3, 25), d(2253, 3, 31), d(2253, 4, 9)));

        assertThat(origins.stream().map(this::chargedDaysOf).toList()).containsExactly(0, 9, 14, 20, 29);
    }
}
