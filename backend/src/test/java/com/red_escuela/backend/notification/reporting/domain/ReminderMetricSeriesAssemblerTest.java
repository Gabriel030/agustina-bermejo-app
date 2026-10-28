package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-343")
class ReminderMetricSeriesAssemblerTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final MonthWindow MARCH_AND_APRIL = new MonthWindow(MARCH, APRIL);
    private static final Instant TENTH_AT_TEN = Instant.parse("2026-03-10T10:00:00Z");
    private static final String BEFORE_DUE_KIND = "OWN_NOTICE:BEFORE_DUE_REMINDER";

    private static ReminderDeliveryCount count(YearMonth month, DeliveryChannel channel, DeliveryStatus status, String reason, String kind, long total) {
        return new ReminderDeliveryCount(month, channel, status, reason, kind, total);
    }

    private static List<BigDecimal> valuesOf(List<ReminderMetricSeries> series, String key) {
        return series.stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow().values();
    }

    private static List<ReminderMetricSeries> assemble(List<ReminderDeliveryCount> counts) {
        return ReminderMetricSeriesAssembler.assemble(MARCH_AND_APRIL, counts, List.of(), Map.of());
    }

    @Test
    @Tag("e01")
    void marchWithSimulatedSuppressedDeferredAndFailedMailsIsBrokenDownByStatusNoticeAndReason() {
        List<ReminderDeliveryCount> counts = List.of(
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT_SIMULATED, null, "OWN_NOTICE:AFTER_ISSUE_REMINDER", 100),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT_SIMULATED, null, BEFORE_DUE_KIND, 200),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT_SIMULATED, null, "DAILY_DIGEST:AFTER_DUE_REMINDER", 100),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT_SIMULATED, null, "OWN_NOTICE:BEFORE_SUSPENSION_REMINDER", 50),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT_SIMULATED, null, "DEBT_SUMMARY:AFTER_DUE_REMINDER", 50),
                count(MARCH, DeliveryChannel.IN_APP, DeliveryStatus.SENT, null, "OWN_NOTICE:ISSUANCE_NOTICE", 120),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SUPPRESSED, "OPTED_OUT", BEFORE_DUE_KIND, 40),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SUPPRESSED, "NO_VERIFIED_EMAIL", BEFORE_DUE_KIND, 10),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.DEFERRED, "DAILY_CAP_REACHED", BEFORE_DUE_KIND, 30),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.FAILED, "PROVIDER_ERROR", BEFORE_DUE_KIND, 5));

        List<ReminderMetricSeries> series = assemble(counts);

        assertThat(valuesOf(series, "sent")).extracting(BigDecimal::intValue).containsExactly(620, 0);
        assertThat(valuesOf(series, "sentByChannel.EMAIL")).extracting(BigDecimal::intValue).containsExactly(500, 0);
        assertThat(valuesOf(series, "sentByChannel.IN_APP")).extracting(BigDecimal::intValue).containsExactly(120, 0);
        assertThat(valuesOf(series, "sentByNotice.ISSUANCE_NOTICE").get(0)).isEqualByComparingTo("120");
        assertThat(valuesOf(series, "sentByNotice.AFTER_ISSUE_REMINDER").get(0)).isEqualByComparingTo("100");
        assertThat(valuesOf(series, "sentByNotice.BEFORE_DUE_REMINDER").get(0)).isEqualByComparingTo("200");
        assertThat(valuesOf(series, "sentByNotice.AFTER_DUE_REMINDER").get(0)).isEqualByComparingTo("100");
        assertThat(valuesOf(series, "sentByNotice.BEFORE_SUSPENSION_REMINDER").get(0)).isEqualByComparingTo("50");
        assertThat(valuesOf(series, "sentByNotice.DEBT_SUMMARY").get(0)).isEqualByComparingTo("50");
        assertThat(valuesOf(series, "suppressed").get(0)).isEqualByComparingTo("50");
        assertThat(valuesOf(series, "suppressedByReason.OPTED_OUT").get(0)).isEqualByComparingTo("40");
        assertThat(valuesOf(series, "suppressedByReason.NO_VERIFIED_EMAIL").get(0)).isEqualByComparingTo("10");
        assertThat(valuesOf(series, "deferred").get(0)).isEqualByComparingTo("30");
        assertThat(valuesOf(series, "deferredByReason.DAILY_CAP_REACHED").get(0)).isEqualByComparingTo("30");
        assertThat(valuesOf(series, "failed").get(0)).isEqualByComparingTo("5");
    }

    @Test
    void everyDeclaredNoticeIsReportedWithZeroWhenNothingWasSent() {
        List<ReminderMetricSeries> series = assemble(List.of());

        assertThat(series).extracting(ReminderMetricSeries::key).contains("sentByNotice.ISSUANCE_NOTICE", "sentByNotice.AFTER_ISSUE_REMINDER",
                "sentByNotice.BEFORE_DUE_REMINDER", "sentByNotice.AFTER_DUE_REMINDER", "sentByNotice.BEFORE_SUSPENSION_REMINDER", "sentByNotice.DEBT_SUMMARY");
        assertThat(valuesOf(series, "sentByNotice.DEBT_SUMMARY")).extracting(BigDecimal::intValue).containsExactly(0, 0);
        assertThat(valuesOf(series, "paidWithin72HoursPercent")).containsExactly(null, null);
    }

    @Test
    void postWithdrawalSummariesAndUnknownKindsAreReportedOnlyWhenTheyWereSent() {
        List<ReminderMetricSeries> series = assemble(List.of(
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT, null, "POST_WITHDRAWAL_SUMMARY", 3),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT, null, "SOMETHING_NEW", 2)));

        assertThat(valuesOf(series, "sentByNotice.POST_WITHDRAWAL_SUMMARY").get(0)).isEqualByComparingTo("3");
        assertThat(valuesOf(series, "sentByNotice.OTHER").get(0)).isEqualByComparingTo("2");
        assertThat(assemble(List.of()).stream().map(ReminderMetricSeries::key)).doesNotContain("sentByNotice.POST_WITHDRAWAL_SUMMARY", "sentByNotice.OTHER");
    }

    @Test
    void queuedAndDiscardedDeliveriesAreNotCountedAsSentSuppressedDeferredOrFailed() {
        List<ReminderMetricSeries> series = assemble(List.of(
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.QUEUED, null, BEFORE_DUE_KIND, 7),
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.DISCARDED, "NO_LONGER_TIMELY", BEFORE_DUE_KIND, 9)));

        for (String key : List.of("sent", "suppressed", "deferred", "failed")) {
            assertThat(valuesOf(series, key).get(0)).isEqualByComparingTo("0");
        }
    }

    @Test
    void deliveriesOutsideTheRequestedMonthsAreIgnoredAndEachMonthKeepsItsOwnCounts() {
        List<ReminderMetricSeries> series = assemble(List.of(
                count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SENT, null, BEFORE_DUE_KIND, 4),
                count(APRIL, DeliveryChannel.EMAIL, DeliveryStatus.SENT, null, BEFORE_DUE_KIND, 6),
                count(YearMonth.of(2026, 5), DeliveryChannel.EMAIL, DeliveryStatus.SENT, null, BEFORE_DUE_KIND, 99)));

        assertThat(valuesOf(series, "sent")).extracting(BigDecimal::intValue).containsExactly(4, 6);
    }

    @Test
    void aDeliveryWithoutReasonIsReportedUnderTheUnspecifiedReason() {
        List<ReminderMetricSeries> series = assemble(List.of(count(MARCH, DeliveryChannel.EMAIL, DeliveryStatus.SUPPRESSED, null, BEFORE_DUE_KIND, 2)));

        assertThat(valuesOf(series, "suppressedByReason.UNSPECIFIED").get(0)).isEqualByComparingTo("2");
    }

    @Test
    @Tag("e02")
    void thirtyOfOneHundredRecipientsPayingWithinSeventyTwoHoursIsThirtyPercent() {
        List<SentReminderEmail> emails = new ArrayList<>();
        Map<Long, List<Instant>> arrivals = new java.util.HashMap<>();
        for (long person = 1; person <= 100; person++) {
            emails.add(new SentReminderEmail(person, MARCH, TENTH_AT_TEN));
            if (person <= 30) {
                arrivals.put(person, List.of(Instant.parse("2026-03-12T09:59:59Z")));
            }
        }

        List<ReminderMetricSeries> series = ReminderMetricSeriesAssembler.assemble(MARCH_AND_APRIL, List.of(), emails, arrivals);

        assertThat(valuesOf(series, "emailsSent").get(0)).isEqualByComparingTo("100");
        assertThat(valuesOf(series, "paidWithin72Hours").get(0)).isEqualByComparingTo("30");
        assertThat(valuesOf(series, "paidWithin72HoursPercent").get(0)).isEqualByComparingTo("30");
        assertThat(valuesOf(series, "paidWithin72HoursPercent").get(1)).isNull();
    }

    @Test
    void eachMailIsJudgedAgainstItsOwnWindowEvenWhenTheSameStudentGotTwoOfThem() {
        Instant firstMail = TENTH_AT_TEN;
        Instant secondMail = Instant.parse("2026-03-20T10:00:00Z");
        List<SentReminderEmail> emails = List.of(new SentReminderEmail(1L, MARCH, firstMail), new SentReminderEmail(1L, MARCH, secondMail));

        List<ReminderMetricSeries> series = ReminderMetricSeriesAssembler.assemble(MARCH_AND_APRIL, List.of(), emails,
                Map.of(1L, List.of(Instant.parse("2026-03-11T10:00:00Z"))));

        assertThat(valuesOf(series, "emailsSent").get(0)).isEqualByComparingTo("2");
        assertThat(valuesOf(series, "paidWithin72Hours").get(0)).isEqualByComparingTo("1");
        assertThat(valuesOf(series, "paidWithin72HoursPercent").get(0)).isEqualByComparingTo("50");
    }

    @Test
    void mailsOfAMonthOutsideTheWindowDoNotCountTowardsThePercentage() {
        List<SentReminderEmail> emails = List.of(new SentReminderEmail(1L, YearMonth.of(2026, 7), TENTH_AT_TEN));

        List<ReminderMetricSeries> series = ReminderMetricSeriesAssembler.assemble(MARCH_AND_APRIL, List.of(), emails, Map.of());

        assertThat(valuesOf(series, "emailsSent")).extracting(BigDecimal::intValue).containsExactly(0, 0);
    }
}
