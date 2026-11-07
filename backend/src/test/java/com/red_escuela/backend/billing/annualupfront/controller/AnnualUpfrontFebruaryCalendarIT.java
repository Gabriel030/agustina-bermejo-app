package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@Tag("D-524")
@Tag("D-131")
class AnnualUpfrontFebruaryCalendarIT extends AbstractAnnualUpfrontRequestIT {

    private void configureFebruaryToDecemberYear() {
        Long termId = jdbc.queryForObject("select term_id from program_year_billing_template where id = ?", Long.class, templateId);
        jdbc.update("update term set start_date = ? where id = ?", java.sql.Timestamp.valueOf(LocalDateTime.of(2027, 2, 1, 0, 0)), termId);
        transactionTemplate.executeWithoutResult(status -> configurationVersions.saveAndFlush(ConfigurationVersion.publish(
                ConfigurationTopic.ofYearTerms(offering.programId(), termId), new YearBillingTerms(11, 2, 2, 8, 1, 10, 10, true, false, true, true, true, false),
                LocalDate.of(2000, 1, 2), "Fixture febrero", director.getId(), LocalDate.now(), null, Instant.now())));
        today(LocalDate.of(2027, 2, 2));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 2, 10, 0));
    }

    @Test
    void aFebruaryToDecemberYearAnnualRequestHasElevenInstallmentLinesWithTheDiscountOnEach() throws Exception {
        configureFebruaryToDecemberYear();
        publishAnnualPromotion();

        JsonNode body = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());

        Long invoiceId = body.get("invoiceId").asLong();
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'MONTHLY_INSTALLMENT'")).isEqualTo(11);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'MONTHLY_INSTALLMENT' and period = date '2027-02-01'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'MONTHLY_INSTALLMENT' and period = date '2027-01-01'")).isZero();
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'ENROLLMENT_FEE'")).isZero();
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + invoiceId + ")")).isEqualTo(11);
        assertThat(new BigDecimal(body.get("total").get("amount").asString(""))).isEqualByComparingTo("792000.00");
    }

    @Test
    void theDefaultMarchToDecemberYearAnnualRequestStillHasTenInstallmentLines() throws Exception {
        publishAnnualPromotion();

        JsonNode body = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());

        Long invoiceId = body.get("invoiceId").asLong();
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'MONTHLY_INSTALLMENT'")).isEqualTo(10);
        assertThat(new BigDecimal(body.get("total").get("amount").asString(""))).isEqualByComparingTo("720000.00");
    }

    @Test
    @Tag("D-520")
    void aPaidFebruaryInstallmentSpendsTheOptionOnlyInAFebruaryToDecemberYear() throws Exception {
        configureFebruaryToDecemberYear();
        Long invoiceId = issueInstallment(java.time.YearMonth.of(2027, 2));
        jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 5, 10, 0));

        JsonNode body = objectMapper.readTree(request(true, "v1", 409).getResponse().getContentAsString());

        assertThat(body.get("violations").get(0).get("code").asString("")).isEqualTo("ANNUAL_UPFRONT_MONTHLY_ALREADY_USED");
    }
}
