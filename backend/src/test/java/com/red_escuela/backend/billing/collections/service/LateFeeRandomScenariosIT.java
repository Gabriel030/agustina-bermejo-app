package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Walks the days of five seeded scenarios with the real late fee reconciliation against Postgres")
class LateFeeRandomScenariosIT extends AbstractLateFeeScenarioIT {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-332")
    void randomScenariosAgainstPostgresKeepTheDaysChargedEqualToTheOracle(int seed) {
        int year = 2260 + seed;
        int grace = seed % 3 == 0 ? 5 : 0;
        arrange(year);
        publish(grace, "0.002", 10, true, true, d(year, 1, 1));
        Random random = new Random(seed);
        LocalDate due = d(year, 3, 1).plusDays(random.nextInt(60));
        LocalDate paidOn = due.plusDays(random.nextInt(100));
        Invoice origin = overdueInstallmentDueOn(due);

        runDays(d(year, 3, 1), paidOn.plusDays(35), List.of(origin), List.of(paidOn));

        long oracle = paidOn.isAfter(due.plusDays(grace)) ? ChronoUnit.DAYS.between(due, paidOn) : 0;
        assertThat(chargedDaysOf(origin)).as("due %s paid %s grace %s", due, paidOn, grace).isEqualTo((int) oracle);
    }
}
