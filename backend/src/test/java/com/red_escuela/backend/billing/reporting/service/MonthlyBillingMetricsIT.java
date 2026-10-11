package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyBillingMetricsIT extends AbstractMonthlyMetricsIT {

    private static final String NO_DISCOUNT = "0.00";

    private void seedManyInvoices(int count, Currency currency, InvoiceType type, InvoiceIssuingReason reason, LocalDate issued, String amount) {
        Long account = currency == Currency.ARS ? arsAccountId : usdAccountId;
        for (int i = 0; i < count; i++) {
            seedInvoice(account, currency, type, reason, issued, issued.plusDays(9), "PENDING", amount, NO_DISCOUNT, null);
        }
    }

    @Test
    @Tag("D-097")
    void issuedInvoicesOfTheChosenCurrencyAreCountedByTypeAndTheOtherCurrencyIsLeftOut() throws Exception {
        LocalDate firstOfMarch = LocalDate.of(2020, 3, 1);
        seedManyInvoices(38, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "10000.00");
        seedManyInvoices(2, Currency.ARS, InvoiceType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "5000.00");
        seedManyInvoices(3, Currency.USD, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "100.00");
        seedManyInvoices(1, Currency.ARS, InvoiceType.ADMINISTRATIVE_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, firstOfMarch, "999.00");

        JsonNode ars = metric(directorToken, "issued-invoices", "from=2020-03&to=2020-03&currency=ARS");
        JsonNode usd = metric(directorToken, "issued-invoices", "from=2020-03&to=2020-03&currency=USD");

        assertThat(ars.get("available").asBoolean()).isTrue();
        assertThat(seriesOf(ars, "count.MONTHLY_INSTALLMENT").get("values").get(0).asLong()).isEqualTo(38);
        assertThat(seriesOf(ars, "count.ENROLLMENT_FEE").get("values").get(0).asLong()).isEqualTo(2);
        assertThat(seriesOf(ars, "amount.MONTHLY_INSTALLMENT").get("values").get(0).get("amount").asText()).isEqualTo("380000.00");
        assertThat(seriesOf(ars, "amount.MONTHLY_INSTALLMENT").get("values").get(0).get("currency").asText()).isEqualTo("ARS");
        assertThat(seriesOf(ars, "amount.ENROLLMENT_FEE").get("values").get(0).get("amount").asText()).isEqualTo("10000.00");
        assertThat(hasSeries(ars, "count.ADMINISTRATIVE_FEE")).isFalse();
        assertThat(seriesOf(usd, "count.MONTHLY_INSTALLMENT").get("values").get(0).asLong()).isEqualTo(3);
        assertThat(seriesOf(usd, "amount.MONTHLY_INSTALLMENT").get("values").get(0).get("amount").asText()).isEqualTo("300.00");
        assertThat(hasSeries(usd, "count.ENROLLMENT_FEE")).isFalse();
    }

    @Test
    @Tag("D-097")
    void everyPanelAsksForTheCurrencyWhenAccountsExistInMoreThanOne() throws Exception {
        for (String panel : new String[]{"issued-invoices", "discounts", "overdue-detected", "late-fees", "on-time-payment", "receipts"}) {
            assertThat(errorOf(directorToken, "/billing/metrics/" + panel + "?from=2020-03&to=2020-03", 400).get("code").asText())
                    .isEqualTo("CURRENCY_REQUIRED");
        }
    }

    @Test
    @Tag("D-097")
    void everyPanelKeepsMoneyOfASingleCurrencyAndNeverSumsBothCurrencies() throws Exception {
        LocalDate firstOfJune = LocalDate.of(2020, 6, 1);
        seedManyInvoices(2, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfJune, "1000.00");
        seedManyInvoices(2, Currency.USD, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfJune, "50.00");

        for (String panel : new String[]{"issued-invoices", "discounts", "overdue-detected", "late-fees", "on-time-payment", "receipts"}) {
            for (Currency currency : Currency.values()) {
                JsonNode body = metric(directorToken, panel, "from=2020-06&to=2020-06&currency=" + currency);
                for (JsonNode series : body.get("series")) {
                    if (series.get("unit").asText().equals("MONEY")) {
                        for (JsonNode value : series.get("values")) {
                            assertThat(value.get("currency").asText()).as(panel + "/" + series.get("key").asText()).isEqualTo(currency.name());
                        }
                    }
                }
            }
        }
        assertThat(seriesOf(metric(directorToken, "issued-invoices", "from=2020-06&to=2020-06&currency=ARS"), "amount.MONTHLY_INSTALLMENT")
                .get("values").get(0).get("amount").asText()).isEqualTo("2000.00");
    }

    @Test
    void overdueInvoicesAreCountedInTheMonthOfTheirBusinessDateEvenWhenRegisteredAtNightOnTheNextDay() throws Exception {
        Long registeredAfterMidnight = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN,
                LocalDate.of(2021, 3, 1), LocalDate.of(2021, 3, 30), "OVERDUE", "7000.00", NO_DISCOUNT, null);
        seedOverdueDetection(registeredAfterMidnight, LocalDate.of(2021, 3, 31), "2021-04-01 03:00:00");
        seedOverdueDetection(registeredAfterMidnight, LocalDate.of(2021, 3, 31), "2021-04-01 03:00:05");
        Long withoutBusinessDate = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN,
                LocalDate.of(2021, 4, 1), LocalDate.of(2021, 4, 10), "OVERDUE", "3000.00", NO_DISCOUNT, null);
        seedOverdueDetection(withoutBusinessDate, null, "2021-04-15 03:00:00");

        JsonNode body = metric(directorToken, "overdue-detected", "from=2021-03&to=2021-04&currency=ARS");

        assertThat(seriesOf(body, "count").get("values").get(0).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "count").get("values").get(1).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "amount").get("values").get(0).get("amount").asText()).isEqualTo("7000.00");
        assertThat(seriesOf(body, "amount").get("values").get(1).get("amount").asText()).isEqualTo("3000.00");
    }

    @Test
    void discountsReportTheForegoneAmountByTypeAndItsPercentageOfTheGrossBilled() throws Exception {
        LocalDate firstOfMarch = LocalDate.of(2022, 3, 1);
        for (int i = 0; i < 3; i++) {
            seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch,
                    firstOfMarch.plusDays(9), "PENDING", "100000.00", "40000.00", "SCHOLARSHIP");
        }
        for (int i = 0; i < 2; i++) {
            seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch,
                    firstOfMarch.plusDays(9), "PENDING", "100000.00", "40000.00", "PROMOTION");
        }
        seedManyInvoices(15, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "100000.00");

        JsonNode body = metric(directorToken, "discounts", "from=2022-03&to=2022-03&currency=ARS");

        assertThat(seriesOf(body, "amount.SCHOLARSHIP").get("values").get(0).get("amount").asText()).isEqualTo("120000.00");
        assertThat(seriesOf(body, "amount.PROMOTION").get("values").get(0).get("amount").asText()).isEqualTo("80000.00");
        assertThat(seriesOf(body, "count.SCHOLARSHIP").get("values").get(0).asLong()).isEqualTo(3);
        assertThat(seriesOf(body, "totalForegone").get("values").get(0).get("amount").asText()).isEqualTo("200000.00");
        assertThat(seriesOf(body, "percentOfGross").get("values").get(0).decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    @Tag("D-150")
    void invoicesIssuedForZeroBecauseOfAFullScholarshipAreCounted() throws Exception {
        LocalDate firstOfMarch = LocalDate.of(2023, 3, 1);
        for (int i = 0; i < 3; i++) {
            seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch,
                    firstOfMarch.plusDays(9), "PAID", "80000.00", "80000.00", "SCHOLARSHIP");
        }
        seedManyInvoices(1, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "80000.00");

        JsonNode body = metric(directorToken, "issued-invoices", "from=2023-03&to=2023-04&currency=ARS");

        assertThat(seriesOf(body, "zeroTotalScholarship").get("values").get(0).asLong()).isEqualTo(3);
        assertThat(seriesOf(body, "zeroTotalScholarship").get("values").get(1).asLong()).isZero();
        assertThat(seriesOf(body, "count.MONTHLY_INSTALLMENT").get("values").get(0).asLong()).isEqualTo(4);
    }

    @Test
    @Tag("D-356")
    void theLateFeePanelIsUnavailableWithoutTheLateFeeChargesAndNeverFails() throws Exception {
        JsonNode body = metric(directorToken, "late-fees", "from=2020-03&to=2020-05&currency=ARS");

        assertThat(body.get("available").asBoolean()).isFalse();
        assertThat(body.get("series")).isEmpty();
        assertThat(body.get("months")).hasSize(3);
    }

    @Test
    @Tag("D-356")
    void theLateFeePanelBecomesAvailableOnceTheLateFeeChargesExist() throws Exception {
        jdbc.execute("create table late_fee_charge (id bigint)");
        try {
            JsonNode body = metric(directorToken, "late-fees", "from=2020-03&to=2020-04&currency=ARS");

            assertThat(body.get("available").asBoolean()).isTrue();
            assertThat(seriesOf(body, "issuedCount").get("values").get(0).asLong()).isZero();
            assertThat(seriesOf(body, "annulledAmount").get("values").get(1).get("amount").asText()).isEqualTo("0.00");
        } finally {
            jdbc.execute("drop table late_fee_charge");
        }
    }

    @Test
    @Tag("D-154")
    @Tag("D-243")
    void onTimePaymentIsThePercentageOfInvoicesPaidOnOrBeforeTheirDueDateAndTheAverageDelayOfTheLateOnes() throws Exception {
        LocalDate firstOfMarch = LocalDate.of(2024, 3, 1);
        LocalDate due = LocalDate.of(2024, 3, 10);
        for (int i = 0; i < 8; i++) {
            Long invoice = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, due,
                    "PAID", "10000.00", NO_DISCOUNT, null);
            seedAllocation(arsAccountId, invoice, Currency.ARS, "10000.00", due.minusDays(i % 3));
        }
        Long late = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, due,
                "PAID", "10000.00", NO_DISCOUNT, null);
        seedAllocation(arsAccountId, late, Currency.ARS, "10000.00", due.plusDays(5));
        seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, due, "OVERDUE", "10000.00",
                NO_DISCOUNT, null);

        JsonNode body = metric(directorToken, "on-time-payment", "from=2024-03&to=2024-03&currency=ARS");

        assertThat(seriesOf(body, "onTimePercent").get("values").get(0).decimalValue()).isEqualByComparingTo("80");
        assertThat(seriesOf(body, "averageDelayDays").get("values").get(0).decimalValue()).isEqualByComparingTo("5");
        assertThat(seriesOf(body, "consideredInvoices").get("values").get(0).asLong()).isEqualTo(10);
        assertThat(seriesOf(body, "onTimeInvoices").get("values").get(0).asLong()).isEqualTo(8);
        assertThat(seriesOf(body, "collected").get("values").get(0).get("amount").asText()).isEqualTo("90000.00");
    }

    @Test
    @Tag("D-154")
    void unpaidInvoicesThatAreNotDueYetAndInvoicesUnderReviewDoNotCountAgainstTheMonth() throws Exception {
        LocalDate issued = LocalDate.of(2024, 5, 1);
        Long paid = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued, issued.plusDays(9),
                "PAID", "10000.00", NO_DISCOUNT, null);
        seedAllocation(arsAccountId, paid, Currency.ARS, "10000.00", issued.plusDays(2));
        seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued, issued.plusDays(9),
                "PAYMENT_UNDER_REVIEW", "10000.00", NO_DISCOUNT, null);
        seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued, LocalDate.of(2999, 1, 1),
                "PENDING", "10000.00", NO_DISCOUNT, null);

        JsonNode body = metric(directorToken, "on-time-payment", "from=2024-05&to=2024-05&currency=ARS");

        assertThat(seriesOf(body, "consideredInvoices").get("values").get(0).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "onTimePercent").get("values").get(0).decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void receiptsReportTheirOutcomeAndHowManyWereReviewedAfterThreeBusinessDays() throws Exception {
        String[] statuses = {"APPROVED", "APPROVED", "APPLIED_TO_DEBT", "REJECTED"};
        String[] reviewedAt = {"2025-03-04 10:00:00", "2025-03-05 10:00:00", "2025-03-06 10:00:00", "2025-03-07 10:00:00"};
        for (int i = 0; i < 4; i++) {
            Long invoice = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 10), "PENDING", "10000.00", NO_DISCOUNT, null);
            jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, reviewed_at) values (?, 'receipts/test.pdf', "
                    + "timestamp '2025-03-03 10:00:00', ?, cast(? as timestamp))", invoice, statuses[i], reviewedAt[i]);
        }

        JsonNode body = metric(directorToken, "receipts", "from=2025-03&to=2025-03&currency=ARS");

        assertThat(seriesOf(body, "uploaded").get("values").get(0).asLong()).isEqualTo(4);
        assertThat(seriesOf(body, "approved").get("values").get(0).asLong()).isEqualTo(2);
        assertThat(seriesOf(body, "appliedToDebt").get("values").get(0).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "rejected").get("values").get(0).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "reviewedAfterTarget").get("values").get(0).asLong()).isEqualTo(1);
        assertThat(seriesOf(body, "averageReviewBusinessDays").get("values").get(0).decimalValue()).isEqualByComparingTo("2.5");
        assertThat(body.has("goal")).isFalse();
    }

    @Test
    @Tag("D-357")
    void theOnTimePaymentPanelMarksTheMonthsAgainstTheDefaultGoalOfEightyFivePercent() throws Exception {
        LocalDate due = LocalDate.of(2020, 9, 10);
        seedOnTimeMonth(LocalDate.of(2020, 9, 1), due, 8, 2);
        seedOnTimeMonth(LocalDate.of(2020, 10, 1), LocalDate.of(2020, 10, 10), 9, 1);

        JsonNode body = metric(directorToken, "on-time-payment", "from=2020-09&to=2020-10&currency=ARS");

        assertThat(seriesOf(body, "onTimePercent").get("values").get(0).decimalValue()).isEqualByComparingTo("80");
        assertThat(seriesOf(body, "onTimePercent").get("values").get(1).decimalValue()).isEqualByComparingTo("90");
        assertThat(body.get("goal").get("indicator").asText()).isEqualTo("ON_TIME_PAYMENT");
        assertThat(body.get("goal").get("comparator").asText()).isEqualTo("AT_LEAST");
        assertThat(body.get("goal").get("thresholdPercent").decimalValue()).isEqualByComparingTo("85");
        assertThat(body.get("goal").get("evaluation").get(0).asText()).isEqualTo("BELOW");
        assertThat(body.get("goal").get("evaluation").get(1).asText()).isEqualTo("MET");
    }

    @Test
    void aMonthWithoutPaidOrDueInvoicesHasNoPercentageAndNoGoalEvaluation() throws Exception {
        JsonNode body = metric(directorToken, "on-time-payment", "from=2019-01&to=2019-01&currency=ARS");

        assertThat(seriesOf(body, "onTimePercent").get("values").get(0).isNull()).isTrue();
        assertThat(body.get("goal").get("evaluation").get(0).asText()).isEqualTo("NO_DATA");
    }

    private void seedOnTimeMonth(LocalDate issued, LocalDate due, int onTime, int late) {
        for (int i = 0; i < onTime + late; i++) {
            Long invoice = seedInvoice(arsAccountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued, due,
                    "PAID", "10000.00", NO_DISCOUNT, null);
            seedAllocation(arsAccountId, invoice, Currency.ARS, "10000.00", i < onTime ? due : due.plusDays(3));
        }
    }

    @Test
    void invalidRangesAndUnknownPanelsAreRejectedWithTheirCode() throws Exception {
        assertThat(errorOf(directorToken, "/billing/metrics/discounts?currency=ARS&from=2026-03&to=2029-12", 400).get("code").asText())
                .isEqualTo("INVALID_MONTH_RANGE");
        assertThat(errorOf(directorToken, "/billing/metrics/discounts?currency=ARS&from=2026-04&to=2026-03", 400).get("code").asText())
                .isEqualTo("INVALID_MONTH_RANGE");
        assertThat(errorOf(directorToken, "/billing/metrics/collections-stock?currency=ARS", 404).get("code").asText())
                .isEqualTo("UNKNOWN_METRIC_PANEL");
    }

    @Test
    void theProgramCourseAndInvoiceTypeFiltersNarrowEveryPanel() throws Exception {
        LocalDate firstOfMarch = LocalDate.of(2020, 12, 1);
        seedManyInvoices(2, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "10000.00");
        seedManyInvoices(1, Currency.ARS, InvoiceType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, firstOfMarch, "10000.00");

        for (String panel : new String[]{"issued-invoices", "discounts", "overdue-detected", "late-fees", "on-time-payment", "receipts"}) {
            JsonNode byProgram = metric(directorToken, panel, "from=2020-12&to=2020-12&currency=ARS&programId=999999999&courseId=999999999");
            assertThat(byProgram.get("panel").asText()).isEqualTo(panel);
        }
        JsonNode byType = metric(directorToken, "issued-invoices", "from=2020-12&to=2020-12&currency=ARS&invoiceType=ENROLLMENT_FEE");
        assertThat(hasSeries(byType, "count.MONTHLY_INSTALLMENT")).isFalse();
        assertThat(seriesOf(byType, "count.ENROLLMENT_FEE").get("values").get(0).asLong()).isEqualTo(1);
        JsonNode byProgram = metric(directorToken, "issued-invoices", "from=2020-12&to=2020-12&currency=ARS&programId=999999999");
        assertThat(hasSeries(byProgram, "count.MONTHLY_INSTALLMENT")).isFalse();
    }
}
