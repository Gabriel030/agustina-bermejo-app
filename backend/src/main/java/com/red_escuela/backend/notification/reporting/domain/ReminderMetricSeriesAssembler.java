package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReminderMetricSeriesAssembler {

    public static final String SENT = "sent";
    public static final String SUPPRESSED = "suppressed";
    public static final String DEFERRED = "deferred";
    public static final String FAILED = "failed";
    public static final String SENT_BY_CHANNEL_PREFIX = "sentByChannel.";
    public static final String SENT_BY_NOTICE_PREFIX = "sentByNotice.";
    public static final String SUPPRESSED_BY_REASON_PREFIX = "suppressedByReason.";
    public static final String DEFERRED_BY_REASON_PREFIX = "deferredByReason.";
    public static final String EMAILS_SENT = "emailsSent";
    public static final String PAID_WITHIN_72_HOURS = "paidWithin72Hours";
    public static final String PAID_WITHIN_72_HOURS_PERCENT = "paidWithin72HoursPercent";
    public static final String NO_REASON = "UNSPECIFIED";

    private ReminderMetricSeriesAssembler() {
    }

    public static List<ReminderMetricSeries> assemble(MonthWindow window, List<ReminderDeliveryCount> counts, List<SentReminderEmail> sentEmails,
                                                      Map<Long, List<Instant>> paymentArrivalsByPerson) {
        ReminderMetricSeriesBuilder series = new ReminderMetricSeriesBuilder(window);
        declareFixedSeries(series);
        counts.forEach(count -> tally(series, count));
        tallyPaymentFollowUp(series, window, sentEmails, paymentArrivalsByPerson);
        return series.build();
    }

    private static void declareFixedSeries(ReminderMetricSeriesBuilder series) {
        series.declareCount(SENT).declareCount(SUPPRESSED).declareCount(DEFERRED).declareCount(FAILED);
        Arrays.stream(DeliveryChannel.values()).forEach(channel -> series.declareCount(SENT_BY_CHANNEL_PREFIX + channel));
        Arrays.stream(ReminderNotice.values()).filter(ReminderNotice::isAlwaysReported).forEach(notice -> series.declareCount(SENT_BY_NOTICE_PREFIX + notice));
        series.declareCount(EMAILS_SENT).declareCount(PAID_WITHIN_72_HOURS).declarePercent(PAID_WITHIN_72_HOURS_PERCENT);
    }

    private static void tally(ReminderMetricSeriesBuilder series, ReminderDeliveryCount count) {
        String reason = count.reason() == null ? NO_REASON : count.reason();
        switch (count.status()) {
            case SENT, SENT_SIMULATED -> {
                series.addCount(SENT, count.month(), count.count());
                series.addCount(SENT_BY_CHANNEL_PREFIX + count.channel(), count.month(), count.count());
                series.addCount(SENT_BY_NOTICE_PREFIX + ReminderNotice.ofDeliveryKind(count.kind()), count.month(), count.count());
            }
            case SUPPRESSED -> {
                series.addCount(SUPPRESSED, count.month(), count.count());
                series.addCount(SUPPRESSED_BY_REASON_PREFIX + reason, count.month(), count.count());
            }
            case DEFERRED -> {
                series.addCount(DEFERRED, count.month(), count.count());
                series.addCount(DEFERRED_BY_REASON_PREFIX + reason, count.month(), count.count());
            }
            case FAILED -> series.addCount(FAILED, count.month(), count.count());
            case QUEUED, DISCARDED -> {
            }
        }
    }

    private static void tallyPaymentFollowUp(ReminderMetricSeriesBuilder series, MonthWindow window, List<SentReminderEmail> sentEmails,
                                             Map<Long, List<Instant>> paymentArrivalsByPerson) {
        Map<YearMonth, long[]> emailsAndPaidByMonth = new HashMap<>();
        for (SentReminderEmail email : sentEmails) {
            if (!window.contains(email.month())) {
                continue;
            }
            long[] tally = emailsAndPaidByMonth.computeIfAbsent(email.month(), month -> new long[2]);
            tally[0]++;
            if (ReminderPaymentFollowUp.isFollowedByPayment(email.sentAt(), paymentArrivalsByPerson.getOrDefault(email.recipientPersonId(), List.of()))) {
                tally[1]++;
            }
        }
        emailsAndPaidByMonth.forEach((month, tally) -> {
            series.addCount(EMAILS_SENT, month, tally[0]);
            series.addCount(PAID_WITHIN_72_HOURS, month, tally[1]);
            Percentage.ratio(tally[1], tally[0]).ifPresent(percentage -> series.putPercent(PAID_WITHIN_72_HOURS_PERCENT, month, percentage));
        });
    }
}
