package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectionsStockMetricIT extends AbstractMonthlyMetricsIT {

    private static final String NO_DISCOUNT = "0.00";

    @Autowired
    private CollectionsDailySnapshotStep snapshotStep;

    @BeforeEach
    @AfterEach
    void clearSnapshots() {
        jdbc.update("delete from collections_daily_snapshot");
    }

    private void seedOverdue(Currency currency, LocalDate dueDate, String amount, Long programId) {
        Long accountId = newAccountId(newStudentId(), currency);
        Long invoiceId = seedInvoice(accountId, currency, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, dueDate.minusDays(9), dueDate, "OVERDUE", amount,
                NO_DISCOUNT, null);
        enrollInvoiceStudentInProgram(invoiceId, programId);
    }

    private static double amountOf(JsonNode metric, String key, int monthIndex) {
        return seriesOf(metric, key).get("values").get(monthIndex).get("amount").asDouble();
    }

    @Test
    void whenTheLastDayOfTheMonthHasNoPhotoTheLastOneOfThatMonthIsUsedAndFlaggedAsPartial() throws Exception {
        Long program = seedProgram(Currency.ARS);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 1), "40000.00", program);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 29));
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 2), "60000.00", program);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 30));

        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + program);

        assertThat(metric.get("partial").asBoolean()).isTrue();
        assertThat(amountOf(metric, "amount.FROM_1_TO_30", 0)).isEqualTo(100000.00);
        assertThat(seriesOf(metric, "students").get("values").get(0).asLong()).isEqualTo(2);
    }

    @Test
    void whenThePhotoOfTheLastDayOfTheMonthExistsItIsUsedAndTheMetricIsNotPartial() throws Exception {
        Long program = seedProgram(Currency.ARS);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 1), "40000.00", program);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 30));
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 2), "60000.00", program);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + program);

        assertThat(metric.get("partial").asBoolean()).isFalse();
        assertThat(amountOf(metric, "amount.FROM_1_TO_30", 0)).isEqualTo(100000.00);
    }

    @Test
    void monthsWithoutAnyPhotoAreReportedWithoutDataInsteadOfZero() throws Exception {
        Long program = seedProgram(Currency.ARS);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 1), "40000.00", program);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-02&to=2022-04&currency=ARS&programId=" + program);

        assertThat(metric.get("available").asBoolean()).isTrue();
        assertThat(seriesOf(metric, "amount.FROM_1_TO_30").get("values").get(0).isNull()).isTrue();
        assertThat(amountOf(metric, "amount.FROM_1_TO_30", 1)).isEqualTo(40000.00);
        assertThat(seriesOf(metric, "amount.FROM_1_TO_30").get("values").get(2).isNull()).isTrue();
        assertThat(seriesOf(metric, "students").get("values").get(0).isNull()).isTrue();
    }

    @Test
    void aProgramWithoutDebtOnAMonthThatHasPhotoReportsZeroNotWithoutData() throws Exception {
        Long programWithDebt = seedProgram(Currency.ARS);
        Long programWithoutDebt = seedProgram(Currency.ARS);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 1), "40000.00", programWithDebt);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + programWithoutDebt);

        assertThat(amountOf(metric, "amount.TOTAL", 0)).isEqualTo(0.0);
        assertThat(seriesOf(metric, "students").get("values").get(0).asLong()).isZero();
    }

    @Test
    void filteringByProgramReadsOnlyTheRowsOfThatProgramAndTheTotalAddsEveryBucket() throws Exception {
        Long programA = seedProgram(Currency.ARS);
        Long programB = seedProgram(Currency.ARS);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 21), "10000.00", programA);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 2, 1), "20000.00", programA);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 21), "5000.00", programB);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode ofA = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + programA);
        JsonNode ofB = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + programB);

        assertThat(amountOf(ofA, "amount.FROM_1_TO_30", 0)).isEqualTo(10000.00);
        assertThat(amountOf(ofA, "amount.FROM_31_TO_60", 0)).isEqualTo(20000.00);
        assertThat(amountOf(ofA, "amount.TOTAL", 0)).isEqualTo(30000.00);
        assertThat(seriesOf(ofA, "students").get("values").get(0).asLong()).isEqualTo(2);
        assertThat(amountOf(ofB, "amount.TOTAL", 0)).isEqualTo(5000.00);
    }

    @Test
    void filteringByCourseReadsTheRowOfThatCourse() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = seedInvoice(accountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, LocalDate.of(2022, 3, 1), LocalDate.of(2022, 3, 10),
                "OVERDUE", "7000.00", NO_DISCOUNT, null);
        Long course = seedCourseEnrollmentOn(invoiceId, Currency.ARS);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&courseId=" + course);

        assertThat(amountOf(metric, "amount.FROM_1_TO_30", 0)).isEqualTo(7000.00);
    }

    @Test
    void aProgramAndACourseAtTheSameTimeAreNotAvailableBecauseThePhotoKeepsOneScopePerRow() throws Exception {
        JsonNode metric = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=1&courseId=1");

        assertThat(metric.get("available").asBoolean()).isFalse();
    }

    @Test
    @Tag("D-097")
    void eachCurrencyReadsItsOwnRowsAndTheOtherCurrencyIsNeverAdded() throws Exception {
        Long arsProgram = seedProgram(Currency.ARS);
        Long usdProgram = seedProgram(Currency.USD);
        seedOverdue(Currency.ARS, LocalDate.of(2022, 3, 21), "90000.00", arsProgram);
        seedOverdue(Currency.USD, LocalDate.of(2022, 3, 21), "120.00", usdProgram);
        snapshotStep.takeCollectionsSnapshot(LocalDate.of(2022, 3, 31));

        JsonNode ars = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + arsProgram);
        JsonNode usd = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=USD&programId=" + usdProgram);
        JsonNode arsAskedWithUsdProgram = metric(directorToken, "collections-stock", "from=2022-03&to=2022-03&currency=ARS&programId=" + usdProgram);

        assertThat(amountOf(ars, "amount.TOTAL", 0)).isEqualTo(90000.00);
        assertThat(seriesOf(ars, "amount.TOTAL").get("values").get(0).get("currency").asText()).isEqualTo("ARS");
        assertThat(amountOf(usd, "amount.TOTAL", 0)).isEqualTo(120.00);
        assertThat(seriesOf(usd, "amount.TOTAL").get("values").get(0).get("currency").asText()).isEqualTo("USD");
        assertThat(amountOf(arsAskedWithUsdProgram, "amount.TOTAL", 0)).isEqualTo(0.0);
    }

    @Test
    @Tag("D-355")
    void directorAndAdministrativeReadTheStockWhilePreceptorAndStudentAreForbidden() throws Exception {
        String path = "/billing/metrics/collections-stock?currency=ARS&from=2022-03&to=2022-03";

        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ADMINISTRATIVO").token())).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("PRECEPTOR").token())).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ALUMNO").token())).andExpect(status().isForbidden());
    }
}
