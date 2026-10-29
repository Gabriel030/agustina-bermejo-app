package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NightlyHealthMetricIT extends AbstractMonthlyMetricsIT {

    private static final String COMPLETED = "COMPLETED";
    private static final String COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";
    private static final String FAILED = "FAILED";
    private static final String APRIL = "from=2023-04&to=2023-04&currency=ARS";

    private final List<Long> nightIds = new ArrayList<>();

    @AfterEach
    void deleteNights() {
        MutableClock.INSTANCE.reset();
        nightIds.forEach(id -> {
            jdbc.update("delete from billing_run_item where billing_run_id in (select id from billing_run where nightly_billing_run_id = ?)", id);
            jdbc.update("delete from billing_run where nightly_billing_run_id = ?", id);
            jdbc.update("delete from nightly_billing_run where id = ?", id);
        });
        nightIds.clear();
    }

    private Long newNight(LocalDate day, String status, int durationSeconds) {
        Long id = jdbc.queryForObject("insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at) "
                        + "values (?, ?, 'SCHEDULED', cast(? as timestamp), cast(? as timestamp)) returning id", Long.class, day, status,
                day + " 03:00:00", day.atStartOfDay().plusHours(3).plusSeconds(durationSeconds).toString().replace('T', ' '));
        nightIds.add(id);
        return id;
    }

    private Long newStepRun(Long nightId, String step, int skipped, int failed) {
        return jdbc.queryForObject("insert into billing_run (nightly_billing_run_id, step, status, candidates, processed, skipped, failed) "
                + "values (?, ?, 'COMPLETED_WITH_ERRORS', ?, 0, ?, ?) returning id", Long.class, nightId, step, skipped + failed, skipped, failed);
    }

    private void newHeldAccount(Long stepRunId, long accountId) {
        jdbc.update("insert into billing_run_item (billing_run_id, subject_type, subject_id, status, decision_code) "
                + "values (?, 'CURRENT_ACCOUNT', ?, 'SKIPPED', 'INCONSISTENT_ACCOUNT')", stepRunId, accountId);
    }

    private void newNightlyRunsOfApril(LocalDate missing, LocalDate partial) {
        for (int day = 1; day <= 30; day++) {
            LocalDate date = LocalDate.of(2023, 4, day);
            if (date.equals(missing)) {
                continue;
            }
            newNight(date, date.equals(partial) ? COMPLETED_WITH_ERRORS : COMPLETED, 60);
        }
    }

    private static long countOf(JsonNode metric, String key, int monthIndex) {
        return seriesOf(metric, key).get("values").get(monthIndex).asLong();
    }

    @Test
    @Tag("D-356")
    void aprilWithOnePartialAndOneMissingNightCountsTwentyEightOneAndOneMissed() throws Exception {
        newNightlyRunsOfApril(LocalDate.of(2023, 4, 15), LocalDate.of(2023, 4, 20));

        JsonNode april = metric(directorToken, "nightly-health", APRIL);

        assertThat(countOf(april, "nightsCompleted", 0)).isEqualTo(28);
        assertThat(countOf(april, "nightsCompletedWithErrors", 0)).isEqualTo(1);
        assertThat(countOf(april, "nightsFailed", 0)).isZero();
        assertThat(countOf(april, "nightsMissed", 0)).isEqualTo(1);
    }

    @Test
    @Tag("D-356")
    void aFailedNightIsCountedApartFromTheCompletedOnes() throws Exception {
        newNight(LocalDate.of(2023, 4, 1), COMPLETED, 60);
        newNight(LocalDate.of(2023, 4, 2), FAILED, 60);
        newNight(LocalDate.of(2023, 4, 3), FAILED, 60);

        JsonNode april = metric(directorToken, "nightly-health", APRIL);

        assertThat(countOf(april, "nightsCompleted", 0)).isEqualTo(1);
        assertThat(countOf(april, "nightsFailed", 0)).isEqualTo(2);
        assertThat(countOf(april, "nightsMissed", 0)).isEqualTo(27);
    }

    @Test
    @Tag("D-356")
    void durationsAreTheAverageAndTheLongestOfTheFinishedNightsInSeconds() throws Exception {
        newNight(LocalDate.of(2023, 4, 1), COMPLETED, 100);
        newNight(LocalDate.of(2023, 4, 2), COMPLETED, 201);
        newNight(LocalDate.of(2023, 4, 3), FAILED, 900);

        JsonNode april = metric(directorToken, "nightly-health", APRIL);

        assertThat(countOf(april, "averageDurationSeconds", 0)).isEqualTo(400);
        assertThat(countOf(april, "longestDurationSeconds", 0)).isEqualTo(900);
    }

    @Test
    @Tag("D-356")
    void aMonthWithoutNightsHasNoDurationAndNoActivity() throws Exception {
        newNight(LocalDate.of(2023, 4, 1), COMPLETED, 60);

        JsonNode twoMonths = metric(directorToken, "nightly-health", "from=2023-03&to=2023-04&currency=ARS");

        assertThat(seriesOf(twoMonths, "averageDurationSeconds").get("values").get(0).isNull()).isTrue();
        assertThat(seriesOf(twoMonths, "longestDurationSeconds").get("values").get(0).isNull()).isTrue();
        assertThat(countOf(twoMonths, "nightsCompleted", 0)).isZero();
        assertThat(countOf(twoMonths, "nightsMissed", 0)).isZero();
        assertThat(countOf(twoMonths, "nightsCompleted", 1)).isEqualTo(1);
    }

    @Test
    @Tag("D-356")
    void skippedAndFailedItemsAreSummedPerStepAndHeldAccountsCountOncePerAccount() throws Exception {
        Long first = newNight(LocalDate.of(2023, 4, 1), COMPLETED_WITH_ERRORS, 60);
        Long second = newNight(LocalDate.of(2023, 4, 2), COMPLETED_WITH_ERRORS, 60);
        Long outsideApril = newNight(LocalDate.of(2023, 5, 1), COMPLETED_WITH_ERRORS, 60);
        Long overdueFirst = newStepRun(first, "OVERDUE_DETECTION", 2, 1);
        newStepRun(second, "OVERDUE_DETECTION", 3, 4);
        newStepRun(first, "LATE_FEE_ACCRUAL", 0, 5);
        newStepRun(outsideApril, "OVERDUE_DETECTION", 100, 100);
        newHeldAccount(overdueFirst, 71);
        newHeldAccount(overdueFirst, 72);
        newHeldAccount(newStepRun(second, "ACCOUNT_CREDIT_ALLOCATION", 1, 0), 71);

        JsonNode april = metric(directorToken, "nightly-health", APRIL);

        assertThat(countOf(april, "skippedItems.OVERDUE_DETECTION", 0)).isEqualTo(5);
        assertThat(countOf(april, "failedItems.OVERDUE_DETECTION", 0)).isEqualTo(5);
        assertThat(countOf(april, "failedItems.LATE_FEE_ACCRUAL", 0)).isEqualTo(5);
        assertThat(countOf(april, "skippedItems.LATE_FEE_ACCRUAL", 0)).isZero();
        assertThat(countOf(april, "skippedItems.ACCOUNT_CREDIT_ALLOCATION", 0)).isEqualTo(1);
        assertThat(countOf(april, "failedItems.GATEWAY_SYNC", 0)).isZero();
        assertThat(countOf(april, "heldAccounts", 0)).isEqualTo(2);
    }

    @Test
    @Tag("D-356")
    void eachNightBelongsToTheMonthOfItsBusinessDate() throws Exception {
        newNight(LocalDate.of(2023, 3, 31), COMPLETED, 60);
        newNight(LocalDate.of(2023, 4, 30), COMPLETED, 60);
        newNight(LocalDate.of(2023, 5, 1), FAILED, 60);

        JsonNode months = metric(directorToken, "nightly-health", "from=2023-03&to=2023-05&currency=ARS");

        assertThat(countOf(months, "nightsCompleted", 0)).isEqualTo(1);
        assertThat(countOf(months, "nightsCompleted", 1)).isEqualTo(1);
        assertThat(countOf(months, "nightsFailed", 1)).isZero();
        assertThat(countOf(months, "nightsFailed", 2)).isEqualTo(1);
    }

    @Test
    @Tag("D-356")
    void theDayOfTheQueryAndTheFutureAreNotCountedAsMissedNights() throws Exception {
        newNight(LocalDate.of(2023, 4, 1), COMPLETED, 60);
        newNight(LocalDate.of(2023, 4, 2), COMPLETED, 60);
        MutableClock.INSTANCE.setDate(LocalDate.of(2023, 4, 6));

        JsonNode april = metric(directorToken, "nightly-health", APRIL);

        assertThat(countOf(april, "nightsMissed", 0)).isEqualTo(3);
    }

    @Test
    @Tag("D-356")
    void aProgramOrCourseFilterMakesThePanelNotAvailableBecauseTheNightIsGlobal() throws Exception {
        assertThat(metric(directorToken, "nightly-health", APRIL + "&programId=1").get("available").asBoolean()).isFalse();
        assertThat(metric(directorToken, "nightly-health", APRIL + "&courseId=1").get("available").asBoolean()).isFalse();
    }

    @Test
    @Tag("D-355")
    void directorAndAdministrativeReadThePanelWhilePreceptorAndStudentAreForbidden() throws Exception {
        String path = "/billing/metrics/nightly-health?currency=ARS&from=2023-04&to=2023-04";

        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ADMINISTRATIVO").token())).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("PRECEPTOR").token())).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ALUMNO").token())).andExpect(status().isForbidden());
    }

    @Test
    void theRangeIsLimitedToThirtySixMonthsAndTheCurrencyIsRequiredWithTwoCurrencies() throws Exception {
        assertThat(errorOf(directorToken, "/billing/metrics/nightly-health?currency=ARS&from=2018-01&to=2022-01", 400).get("code").asText())
                .isEqualTo("INVALID_MONTH_RANGE");
        assertThat(errorOf(directorToken, "/billing/metrics/nightly-health?from=2023-04&to=2023-04", 400).get("code").asText())
                .isEqualTo("CURRENCY_REQUIRED");
    }
}
