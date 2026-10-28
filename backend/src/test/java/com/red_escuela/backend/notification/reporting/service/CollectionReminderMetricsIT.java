package com.red_escuela.backend.notification.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.api.PaymentArrival;
import com.red_escuela.backend.billing.api.PaymentFactsQuery;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectionReminderMetricsIT extends AbstractCreditNoteIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEDUP_PREFIX = "reminder-metrics-it:";
    private static final String PATH = "/notification-metrics/collection-reminders";
    private static final String MARCH_2031 = "from=2031-02&to=2031-04";
    private static final LocalDateTime TENTH_AT_TEN = LocalDateTime.of(2031, 3, 10, 10, 0);
    private static final AtomicInteger BATCHES = new AtomicInteger();

    @Autowired
    private PaymentFactsQuery paymentFacts;

    private final List<Long> seededPaymentIds = new ArrayList<>();

    @AfterEach
    void deleteSeededRows() {
        jdbc.update("delete from notification_delivery where dedup_key like ?", DEDUP_PREFIX + "%");
        if (seededPaymentIds.isEmpty()) {
            return;
        }
        String ids = String.join(",", seededPaymentIds.stream().map(String::valueOf).toList());
        jdbc.execute("alter table payment_status_change disable trigger trg_payment_status_change_append_only");
        try {
            jdbc.update("delete from payment_status_change where payment_id in (" + ids + ")");
            jdbc.update("delete from payment where id in (" + ids + ")");
        } finally {
            jdbc.execute("alter table payment_status_change enable trigger trg_payment_status_change_append_only");
            seededPaymentIds.clear();
        }
    }

    private static Instant instantOf(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void seedDeliveries(long total, Long personId, String channel, String status, String reason, String kind, String scheduledFor, LocalDateTime sentAt) {
        String prefix = DEDUP_PREFIX + BATCHES.incrementAndGet() + ":";
        jdbc.update("insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, status_reason, scheduled_for, "
                        + "attempts, subject, body_text, sent_at, created_at) select ? || g, ?, 'COLLECTION_REMINDER', ?, 1, ?, ?, ?, cast(? as date), 0, "
                        + "case when ? = 'EMAIL' then 'Asunto' end, 'Texto', cast(? as timestamptz), now() from generate_series(1, ?) g",
                prefix, channel, kind, personId, status, reason, scheduledFor, channel, sentAt == null ? null : Timestamp.from(instantOf(sentAt)), total);
    }

    private Long newRecipientPersonId() {
        return jdbc.queryForObject("select person_id from student where id = ?", Long.class, newStudentId());
    }

    private Long seedApprovedPayment(Long personId, LocalDateTime approvedAt, String finalStatus) {
        Long studentId = jdbc.queryForObject("select id from student where person_id = ?", Long.class, personId);
        Long accountId = newAccountId(studentId, Currency.ARS);
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(accountId);
        seededPaymentIds.add(paymentId);
        jdbc.update("insert into payment_status_change (payment_id, event, from_status, to_status, actor_process, occurred_at) values (?, 'PAYMENT_APPROVED', 'PENDING', 'APPROVED', 'TEST', ?)",
                paymentId, Timestamp.valueOf(approvedAt));
        if (!finalStatus.equals("APPROVED")) {
            jdbc.update("update payment set status = ? where id = ?", finalStatus, paymentId);
        }
        return paymentId;
    }

    private JsonNode panel(String token, String query) throws Exception {
        String body = mockMvc.perform(get(PATH + "?" + query).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private static JsonNode seriesOf(JsonNode metric, String key) {
        for (JsonNode series : metric.get("series")) {
            if (series.get("key").asText().equals(key)) {
                return series;
            }
        }
        throw new AssertionError("The panel has no series " + key + ": " + metric);
    }

    private static int countAt(JsonNode metric, String key, int monthIndex) {
        return seriesOf(metric, key).get("values").get(monthIndex).asInt();
    }

    @Test
    @Tag("D-343")
    @Tag("e01")
    void marchWithFiveHundredSimulatedMailsAndTheirSuppressedDeferredAndFailedOnesIsBrokenDownByStatusNoticeAndReason() throws Exception {
        String director = newStaff("DIRECTOR").token();
        Long person = newRecipientPersonId();
        LocalDateTime sentAt = LocalDateTime.of(2031, 3, 5, 10, 0);
        seedDeliveries(100, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:AFTER_ISSUE_REMINDER", "2031-03-05", sentAt);
        seedDeliveries(200, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-05", sentAt);
        seedDeliveries(100, person, "EMAIL", "SENT_SIMULATED", null, "DAILY_DIGEST:AFTER_DUE_REMINDER", "2031-03-05", sentAt);
        seedDeliveries(50, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:BEFORE_SUSPENSION_REMINDER", "2031-03-05", sentAt);
        seedDeliveries(50, person, "EMAIL", "SENT_SIMULATED", null, "DEBT_SUMMARY:AFTER_DUE_REMINDER", "2031-03-05", sentAt);
        seedDeliveries(120, person, "IN_APP", "SENT", null, "OWN_NOTICE:ISSUANCE_NOTICE", "2031-03-05", sentAt);
        seedDeliveries(40, person, "EMAIL", "SUPPRESSED", "OPTED_OUT", "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-05", null);
        seedDeliveries(30, person, "EMAIL", "DEFERRED", "DAILY_CAP_REACHED", "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-31", null);
        seedDeliveries(5, person, "EMAIL", "FAILED", "PROVIDER_ERROR", "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-05", null);
        seedDeliveries(7, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-04-01", LocalDateTime.of(2031, 4, 1, 10, 0));
        seedDeliveries(9, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-02-28", LocalDateTime.of(2031, 2, 28, 10, 0));

        JsonNode metric = panel(director, MARCH_2031);

        assertThat(metric.get("panel").asText()).isEqualTo("collection-reminders");
        assertThat(metric.get("months")).extracting(JsonNode::asText).containsExactly("2031-02", "2031-03", "2031-04");
        assertThat(countAt(metric, "sent", 0)).isEqualTo(9);
        assertThat(countAt(metric, "sent", 1)).isEqualTo(620);
        assertThat(countAt(metric, "sent", 2)).isEqualTo(7);
        assertThat(countAt(metric, "sentByChannel.EMAIL", 1)).isEqualTo(500);
        assertThat(countAt(metric, "sentByChannel.IN_APP", 1)).isEqualTo(120);
        assertThat(countAt(metric, "sentByNotice.ISSUANCE_NOTICE", 1)).isEqualTo(120);
        assertThat(countAt(metric, "sentByNotice.AFTER_ISSUE_REMINDER", 1)).isEqualTo(100);
        assertThat(countAt(metric, "sentByNotice.BEFORE_DUE_REMINDER", 1)).isEqualTo(200);
        assertThat(countAt(metric, "sentByNotice.AFTER_DUE_REMINDER", 1)).isEqualTo(100);
        assertThat(countAt(metric, "sentByNotice.BEFORE_SUSPENSION_REMINDER", 1)).isEqualTo(50);
        assertThat(countAt(metric, "sentByNotice.DEBT_SUMMARY", 1)).isEqualTo(50);
        assertThat(countAt(metric, "suppressed", 1)).isEqualTo(40);
        assertThat(countAt(metric, "suppressedByReason.OPTED_OUT", 1)).isEqualTo(40);
        assertThat(countAt(metric, "deferred", 1)).isEqualTo(30);
        assertThat(countAt(metric, "deferredByReason.DAILY_CAP_REACHED", 1)).isEqualTo(30);
        assertThat(countAt(metric, "failed", 1)).isEqualTo(5);
        assertThat(countAt(metric, "failed", 0)).isZero();
        assertThat(countAt(metric, "suppressed", 2)).isZero();
    }

    @Test
    @Tag("e02")
    void thirtyOfOneHundredRecipientsWhoPaidWithinSeventyTwoHoursOfTheMailAreThirtyPercent() throws Exception {
        String director = newStaff("DIRECTOR").token();
        List<Long> persons = new ArrayList<>();
        for (int recipient = 0; recipient < 100; recipient++) {
            Long person = newRecipientPersonId();
            persons.add(person);
            seedDeliveries(1, person, "EMAIL", "SENT_SIMULATED", null, "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-10", TENTH_AT_TEN);
        }
        persons.subList(0, 30).forEach(person -> seedApprovedPayment(person, LocalDateTime.of(2031, 3, 13, 9, 59, 59), "APPROVED"));
        persons.subList(30, 40).forEach(person -> seedApprovedPayment(person, LocalDateTime.of(2031, 3, 13, 10, 0, 1), "APPROVED"));
        persons.subList(40, 45).forEach(person -> seedApprovedPayment(person, LocalDateTime.of(2031, 3, 10, 9, 59, 59), "APPROVED"));
        seedApprovedPayment(persons.get(45), LocalDateTime.of(2031, 3, 11, 10, 0), "CHARGED_BACK");

        JsonNode metric = panel(director, MARCH_2031);

        assertThat(countAt(metric, "emailsSent", 1)).isEqualTo(100);
        assertThat(countAt(metric, "paidWithin72Hours", 1)).isEqualTo(30);
        assertThat(seriesOf(metric, "paidWithin72HoursPercent").get("values").get(1).decimalValue()).isEqualByComparingTo("30");
        assertThat(seriesOf(metric, "paidWithin72HoursPercent").get("values").get(0).isNull()).isTrue();
        assertThat(metric.has("goal")).isFalse();
    }

    @Test
    void aPaymentRefundedAfterwardsStillCountsAsPaidWithinTheWindow() throws Exception {
        String director = newStaff("DIRECTOR").token();
        Long person = newRecipientPersonId();
        seedDeliveries(1, person, "EMAIL", "SENT", null, "OWN_NOTICE:BEFORE_DUE_REMINDER", "2031-03-10", TENTH_AT_TEN);
        seedApprovedPayment(person, LocalDateTime.of(2031, 3, 11, 10, 0), "REFUNDED");

        JsonNode metric = panel(director, MARCH_2031);

        assertThat(countAt(metric, "paidWithin72Hours", 1)).isEqualTo(1);
    }

    @Test
    void paymentFactsReturnTheApprovalInstantOfApprovedAndRefundedPaymentsInsideTheWindowOnly() {
        Long payer = newRecipientPersonId();
        Long chargedBackPayer = newRecipientPersonId();
        Long outsider = newRecipientPersonId();
        seedApprovedPayment(payer, LocalDateTime.of(2031, 3, 11, 10, 0), "APPROVED");
        seedApprovedPayment(payer, LocalDateTime.of(2031, 3, 12, 11, 30), "REFUNDED");
        seedApprovedPayment(payer, LocalDateTime.of(2031, 3, 20, 10, 0), "APPROVED");
        seedApprovedPayment(chargedBackPayer, LocalDateTime.of(2031, 3, 11, 10, 0), "CHARGED_BACK");
        seedApprovedPayment(outsider, LocalDateTime.of(2031, 3, 11, 10, 0), "APPROVED");

        List<PaymentArrival> arrivals = paymentFacts.findPaymentArrivalsOf(List.of(payer, chargedBackPayer),
                instantOf(LocalDateTime.of(2031, 3, 10, 0, 0)), instantOf(LocalDateTime.of(2031, 3, 13, 0, 0)));

        assertThat(arrivals).containsExactly(new PaymentArrival(payer, instantOf(LocalDateTime.of(2031, 3, 11, 10, 0))),
                new PaymentArrival(payer, instantOf(LocalDateTime.of(2031, 3, 12, 11, 30))));
    }

    @Test
    void paymentFactsAreFoundForRecipientsBeyondTheFirstStatementOfAThousand() {
        Long first = newRecipientPersonId();
        Long last = newRecipientPersonId();
        seedApprovedPayment(first, LocalDateTime.of(2031, 3, 11, 10, 0), "APPROVED");
        seedApprovedPayment(last, LocalDateTime.of(2031, 3, 11, 10, 0), "APPROVED");
        List<Long> recipients = new ArrayList<>();
        recipients.add(first);
        for (long filler = 1; filler <= 1500; filler++) {
            recipients.add(-filler);
        }
        recipients.add(last);

        List<PaymentArrival> arrivals = paymentFacts.findPaymentArrivalsOf(recipients, instantOf(LocalDateTime.of(2031, 3, 10, 0, 0)),
                instantOf(LocalDateTime.of(2031, 3, 13, 0, 0)));

        assertThat(arrivals).extracting(PaymentArrival::personId).containsExactlyInAnyOrder(first, last);
    }

    @Test
    @Tag("D-355")
    @Tag("e04")
    void directorAndAdministrativeSeeThePanelWhilePreceptorAndStudentAreForbidden() throws Exception {
        String administrative = newStaff("ADMINISTRATIVO").token();
        String director = newStaff("DIRECTOR").token();
        String preceptor = newStaff("PRECEPTOR").token();
        String student = newStaff("ALUMNO").token();
        String path = PATH + "?from=2031-03&to=2031-03";

        mockMvc.perform(get(path).header("Authorization", "Bearer " + administrative)).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + director)).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + preceptor)).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
    }

    @Test
    void invalidRangesAreRejectedWithTheirCode() throws Exception {
        String director = newStaff("DIRECTOR").token();

        for (String query : List.of("from=2031-03&to=2034-12", "from=2031-04&to=2031-03")) {
            String body = mockMvc.perform(get(PATH + "?" + query).header("Authorization", "Bearer " + director)).andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertThat(JSON.readTree(body).get("code").asText()).isEqualTo("INVALID_MONTH_RANGE");
        }
    }

    @Test
    void withoutDatesThePanelCoversTheLastTwelveMonths() throws Exception {
        String director = newStaff("DIRECTOR").token();

        JsonNode metric = panel(director, "");

        assertThat(metric.get("months")).hasSize(12);
        assertThat(metric.get("available").asBoolean()).isTrue();
    }
}
