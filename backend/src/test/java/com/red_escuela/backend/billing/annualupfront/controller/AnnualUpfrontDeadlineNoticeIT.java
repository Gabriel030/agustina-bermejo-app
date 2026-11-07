package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineNoticePlanner;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@AutoConfigureMockMvc
@Tag("D-349")
@Tag("D-155")
class AnnualUpfrontDeadlineNoticeIT extends AbstractAnnualUpfrontRequestIT {
    private static final LocalDateTime NINE_FIFTY_NINE_ON_THE_THIRD = LocalDateTime.of(2027, 3, 3, 9, 59);
    private static final LocalDateTime TEN_ON_THE_THIRD = LocalDateTime.of(2027, 3, 3, 10, 0);

    @Autowired
    private AnnualUpfrontDeadlineNoticePlanner planner;

    private long requestTheYear() throws Exception {
        JsonNode requested = objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
        return requested.get("id").asLong();
    }

    private int planAt(LocalDateTime moment) {
        MutableClock.INSTANCE.setDateTime(moment);
        return planner.planDeadlineNotices();
    }

    private long deliveriesOf(long requestId, String channel) {
        return count("select count(*) from notification_delivery where dedup_key = 'annual-upfront-deadline-notice:" + requestId + "' and channel = '" + channel + "'");
    }

    private void awaitNoticeOf(long requestId) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(deliveriesOf(requestId, "IN_APP")).isEqualTo(1);
            assertThat(deliveriesOf(requestId, "EMAIL")).isEqualTo(1);
        });
    }

    @Test
    void noNoticeOneMinuteBeforeTheTwentyFourHoursAndOneInAppAndOneMailExactlyAtThem() throws Exception {
        long requestId = requestTheYear();

        assertThat(planAt(NINE_FIFTY_NINE_ON_THE_THIRD)).isZero();
        assertThat(deliveriesOf(requestId, "IN_APP")).isZero();

        assertThat(planAt(TEN_ON_THE_THIRD)).isEqualTo(1);
        awaitNoticeOf(requestId);
        assertThat(count("select count(*) from notification_delivery where dedup_key = 'annual-upfront-deadline-notice:" + requestId + "' and kind = 'ANNUAL_UPFRONT_DEADLINE_NOTICE'"))
                .isEqualTo(2);
    }

    @Test
    void runningTheJobTwiceOrAgainLaterQueuesTheNoticeOnlyOnce() throws Exception {
        long requestId = requestTheYear();

        planAt(TEN_ON_THE_THIRD);
        awaitNoticeOf(requestId);
        planAt(TEN_ON_THE_THIRD.plusMinutes(1));
        planAt(LocalDateTime.of(2027, 3, 4, 9, 0));
        MutableClock.INSTANCE.setDateTime(TEN_ON_THE_THIRD);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(deliveriesOf(requestId, "IN_APP")).isEqualTo(1);
            assertThat(deliveriesOf(requestId, "EMAIL")).isEqualTo(1);
        });
    }

    @Test
    void aRequestWhosePaymentWasAlreadyStartedIsNotNoticed() throws Exception {
        long requestId = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 2, 11, 0));
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", requestId);

        assertThat(planAt(TEN_ON_THE_THIRD)).isZero();
        assertThat(deliveriesOf(requestId, "IN_APP")).isZero();
    }

    @Test
    void aDiscardedOrConfirmedRequestIsNotNoticed() throws Exception {
        long requestId = requestTheYear();

        jdbc.update("update annual_upfront_request set status = 'DISCARDED', discard_reason = 'DEADLINE_EXPIRED', decided_at = ? where id = ?", TEN_ON_THE_THIRD, requestId);
        assertThat(planAt(TEN_ON_THE_THIRD)).isZero();

        jdbc.update("update annual_upfront_request set status = 'CONFIRMED', discard_reason = null where id = ?", requestId);
        assertThat(planAt(TEN_ON_THE_THIRD)).isZero();
        assertThat(deliveriesOf(requestId, "IN_APP")).isZero();
    }

    @Test
    void aDeadlineShorterThanTwentyFourHoursFromTheRequestIsNeverNoticed() throws Exception {
        long requestId = requestTheYear();
        long invoiceId = count("select invoice_id from annual_upfront_request where id = " + requestId);
        jdbc.update("update invoice set payment_deadline = ? where id = ?", LocalDateTime.of(2027, 3, 2, 20, 0), invoiceId);

        assertThat(planAt(LocalDateTime.of(2027, 3, 2, 10, 30))).isZero();
        assertThat(planAt(LocalDateTime.of(2027, 3, 2, 19, 59))).isZero();
        assertThat(deliveriesOf(requestId, "IN_APP")).isZero();
    }

    @Test
    void aRequestPastItsDeadlineIsNotNoticed() throws Exception {
        requestTheYear();

        assertThat(planAt(LocalDateTime.of(2027, 3, 4, 10, 0))).isZero();
    }
}
