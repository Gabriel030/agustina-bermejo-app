package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-520")
@Tag("D-521")
@Tag("D-156")
@Tag("D-131")
class AnnualUpfrontExclusionAndRetryIT extends AbstractAnnualUpfrontRequestIT {

    private static final String MONTHLY_ALREADY_USED = "ANNUAL_UPFRONT_MONTHLY_ALREADY_USED";

    @Autowired
    private AnnualUpfrontDeadlineExpirer expirer;

    private JsonNode quote() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote")
                .header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode requestTheYear() throws Exception {
        return objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
    }

    private String rejectionCodeOfRequest() throws Exception {
        JsonNode body = objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 409).getResponse().getContentAsString());
        return body.get("violations").get(0).get("code").asString("");
    }

    private Long issueMarchInstallmentWithPaymentStatus(String paymentStatus) {
        Long invoiceId = issueInstallment(YearMonth.of(2027, 3));
        jdbc.update("update invoice set payment_status = ?, paid_amount = case when ? = 'PAID' then total else paid_amount end, "
                + "outstanding_amount = case when ? = 'PAID' then 0 else outstanding_amount end where id = ?", paymentStatus, paymentStatus, paymentStatus, invoiceId);
        return invoiceId;
    }

    private void now(LocalDateTime dateTime) {
        MutableClock.INSTANCE.setDateTime(dateTime);
    }

    @Test
    void d520aPayingAMonthlyInstallmentSpendsTheOptionWhileTheDeadlineIsStillOpen() throws Exception {
        issueMarchInstallmentWithPaymentStatus("PAID");
        now(LocalDateTime.of(2027, 3, 10, 12, 0));

        JsonNode quote = quote();

        assertThat(quote.get("eligible").asBoolean()).isFalse();
        assertThat(quote.get("reasonCode").asString("")).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(rejectionCodeOfRequest()).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(count("select count(*) from annual_upfront_request where program_enrollment_id = " + learner.programEnrollmentId())).isZero();
    }

    @Test
    void d520bStartingTheReceiptOfAMonthlyInstallmentSpendsTheOptionToo() throws Exception {
        issueMarchInstallmentWithPaymentStatus("PAYMENT_UNDER_REVIEW");
        now(LocalDateTime.of(2027, 3, 10, 12, 0));

        assertThat(quote().get("reasonCode").asString("")).isEqualTo(MONTHLY_ALREADY_USED);
    }

    @Test
    void d520cAnIssuedButUntouchedMonthlyInstallmentKeepsTheTechnicalReasonOfD131() throws Exception {
        issueMarchInstallmentWithPaymentStatus("PENDING");
        now(LocalDateTime.of(2027, 3, 10, 12, 0));

        assertThat(quote().get("reasonCode").asString("")).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");
    }

    @Test
    void d520dTheSpentOptionKeepsItsReasonUntilTheDeadlinePassesAndThenTheWindowReasonShows() throws Exception {
        issueMarchInstallmentWithPaymentStatus("PAID");
        now(LocalDateTime.of(2027, 3, 30, 23, 59));
        assertThat(quote().get("reasonCode").asString("")).isEqualTo(MONTHLY_ALREADY_USED);

        now(LocalDateTime.of(2027, 3, 31, 23, 59));
        assertThat(quote().get("reasonCode").asString("")).isEqualTo(MONTHLY_ALREADY_USED);

        now(LocalDateTime.of(2027, 4, 1, 0, 0));
        assertThat(quote().get("reasonCode").asString("")).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
    }

    @Test
    void d521aARequestDiscardedByTheExpiryCanBeRequestedAgainWhileTheDeadlineIsOpenAndNoMonthlyWasUsed() throws Exception {
        JsonNode first = requestTheYear();
        now(LocalDateTime.of(2027, 3, 4, 10, 1));
        assertThat(expirer.expireRequestsPastDeadline().expired()).isEqualTo(1);

        JsonNode afterExpiry = quote();
        JsonNode second = requestTheYear();

        assertThat(afterExpiry.get("eligible").asBoolean()).isTrue();
        assertThat(second.get("id").asLong()).isNotEqualTo(first.get("id").asLong());
        assertThat(second.get("status").asString("")).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void d521bAfterTheExpiryTheOptionIsSpentOnceAMonthlyInstallmentIsPaid() throws Exception {
        requestTheYear();
        now(LocalDateTime.of(2027, 3, 4, 10, 1));
        assertThat(expirer.expireRequestsPastDeadline().expired()).isEqualTo(1);
        assertThat(quote().get("eligible").asBoolean()).isTrue();

        issueMarchInstallmentWithPaymentStatus("PAID");
        now(LocalDateTime.of(2027, 3, 5, 10, 0));

        assertThat(quote().get("reasonCode").asString("")).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(rejectionCodeOfRequest()).isEqualTo(MONTHLY_ALREADY_USED);
    }

    @Test
    void d521cAfterTheExpiryNothingIsOfferedOnceTheDeadlinePassesEvenWithoutMonthlyPayments() throws Exception {
        requestTheYear();
        now(LocalDateTime.of(2027, 3, 4, 10, 1));
        assertThat(expirer.expireRequestsPastDeadline().expired()).isEqualTo(1);

        now(LocalDateTime.of(2027, 3, 31, 23, 59));
        assertThat(quote().get("eligible").asBoolean()).isTrue();
        now(LocalDateTime.of(2027, 4, 1, 0, 0));

        assertThat(quote().get("reasonCode").asString("")).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
        assertThat(rejectionCodeOfRequest()).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
    }
}
