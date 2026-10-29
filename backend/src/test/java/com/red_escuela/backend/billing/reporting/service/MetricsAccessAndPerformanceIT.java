package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.testsupport.NightlyTest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsAccessAndPerformanceIT extends AbstractMonthlyMetricsIT {

    private static final List<String> PANELS = List.of("issued-invoices", "discounts", "overdue-detected", "late-fees", "on-time-payment", "receipts", "collections-stock",
            "payment-standing-changes");

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @Tag("D-355")
    void directorAndAdministrativeSeeTheMetricsAndTheDashboardWhilePreceptorAndStudentAreForbidden() throws Exception {
        String administrative = newStaff("ADMINISTRATIVO").token();
        String preceptor = newStaff("PRECEPTOR").token();
        String student = newStaff("ALUMNO").token();
        String metricsPath = "/billing/metrics/issued-invoices?currency=ARS&from=2016-03&to=2016-03";

        for (String path : List.of(metricsPath, "/financial-dashboard")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + administrative)).andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + preceptor)).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    @Tag("D-355")
    void everyPanelRejectsPreceptorAndStudent() throws Exception {
        String preceptor = newStaff("PRECEPTOR").token();
        String student = newStaff("ALUMNO").token();

        for (String panel : PANELS) {
            mockMvc.perform(get("/billing/metrics/" + panel + "?currency=ARS").header("Authorization", "Bearer " + preceptor)).andExpect(status().isForbidden());
            mockMvc.perform(get("/billing/metrics/" + panel + "?currency=ARS").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        }
    }

    @Test
    @NightlyTest(reason = "Performance: seeds twenty four months of data to time every panel")
    void everyPanelRespondsOverTwentyFourMonthsOfDataAndRegistersItsQueryTimer() throws Exception {
        Long[] accounts = new Long[20];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = newAccountId(newStudentId(), Currency.ARS);
        }
        LocalDate start = LocalDate.of(2017, 1, 1);
        for (int month = 0; month < 24; month++) {
            LocalDate issued = start.plusMonths(month);
            for (int student = 0; student < 400; student += 8) {
                Long account = accounts[student % accounts.length];
                Long invoice = seedInvoice(account, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued,
                        issued.plusDays(9), student % 5 == 0 ? "OVERDUE" : "PAID", "10000.00", student % 3 == 0 ? "2000.00" : "0.00",
                        student % 3 == 0 ? "COHORT_PROMOTION" : null);
                if (student % 5 != 0) {
                    seedAllocation(account, invoice, Currency.ARS, student % 3 == 0 ? "8000.00" : "10000.00", issued.plusDays(student % 12));
                } else {
                    seedOverdueDetection(invoice, issued.plusDays(10), issued.plusDays(11).toString() + " 03:00:00");
                }
            }
        }

        for (String panel : PANELS) {
            var body = metric(directorToken, panel, "from=2017-01&to=2018-12&currency=ARS");
            assertThat(body.get("months")).hasSize(24);
            Timer timer = meterRegistry.find("billing.reporting.query").tag("panel", panel).timer();
            assertThat(timer).as("timer of " + panel).isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1);
        }
    }
}
