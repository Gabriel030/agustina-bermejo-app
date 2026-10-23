package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FirstBillingMonthIT extends AbstractMonthlyMetricsIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode firstMonth(String token) throws Exception {
        String body = mockMvc.perform(get("/billing/metrics/first-month").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private void seed(Long accountId, Currency currency, LocalDate issued) {
        seedInvoice(accountId, currency, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, issued, issued.plusDays(9), "PENDING", "1000.00", "0.00", null);
    }

    @Test
    void answersNullWhenThereAreNoInvoices() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from invoice", Long.class)).isZero();

        JsonNode response = firstMonth(directorToken);

        assertThat(response.has("month")).isTrue();
        assertThat(response.get("month").isNull()).isTrue();
    }

    @Test
    void answersTheEarliestIssueMonthAcrossCurrenciesAndMonths() throws Exception {
        seed(arsAccountId, Currency.ARS, LocalDate.of(2001, 7, 15));
        seed(usdAccountId, Currency.USD, LocalDate.of(2001, 4, 30));
        seed(arsAccountId, Currency.ARS, LocalDate.of(2001, 4, 1));
        seed(usdAccountId, Currency.USD, LocalDate.of(2002, 1, 2));

        assertThat(firstMonth(directorToken).get("month").asText()).isEqualTo("2001-04");
        assertThat(firstMonth(newStaff("ADMINISTRATIVO").token()).get("month").asText()).isEqualTo("2001-04");
    }

    @Test
    void rejectsPreceptorAndStudent() throws Exception {
        for (String role : new String[] {"PRECEPTOR", "ALUMNO"}) {
            mockMvc.perform(get("/billing/metrics/first-month").header("Authorization", "Bearer " + newStaff(role).token()))
                    .andExpect(status().isForbidden());
        }
    }
}
