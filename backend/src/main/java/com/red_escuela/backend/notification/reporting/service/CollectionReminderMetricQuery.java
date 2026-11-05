package com.red_escuela.backend.notification.reporting.service;

import com.red_escuela.backend.billing.api.PaymentArrival;
import com.red_escuela.backend.billing.api.PaymentFactsQuery;
import com.red_escuela.backend.billing.api.ReminderPaymentGoalQuery;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.reporting.domain.CollectionReminderMetric;
import com.red_escuela.backend.notification.reporting.domain.MonthWindow;
import com.red_escuela.backend.notification.reporting.domain.ReminderDeliveryCount;
import com.red_escuela.backend.notification.reporting.domain.ReminderMetricSeries;
import com.red_escuela.backend.notification.reporting.domain.ReminderMetricSeriesAssembler;
import com.red_escuela.backend.notification.reporting.domain.ReminderPaymentFollowUp;
import com.red_escuela.backend.notification.reporting.domain.ReminderPaymentGoal;
import com.red_escuela.backend.notification.reporting.domain.SentReminderEmail;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class CollectionReminderMetricQuery {

    private static final String DELIVERIES_OF_MONTHS = """
            where category = 'COLLECTION_REMINDER' and scheduled_for >= :from and scheduled_for < :until""";

    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentFactsQuery paymentFacts;
    private final ReminderPaymentGoalQuery goalQuery;

    public CollectionReminderMetricQuery(NamedParameterJdbcTemplate jdbc, PaymentFactsQuery paymentFacts, ReminderPaymentGoalQuery goalQuery) {
        this.jdbc = jdbc;
        this.paymentFacts = paymentFacts;
        this.goalQuery = goalQuery;
    }

    public CollectionReminderMetric findMetric(MonthWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(window.firstDay()))
                .addValue("until", Date.valueOf(window.dayAfterLastDay()));
        List<ReminderDeliveryCount> counts = findDeliveryCounts(parameters);
        List<SentReminderEmail> sentEmails = findSentEmails(parameters);
        List<ReminderMetricSeries> series = ReminderMetricSeriesAssembler.assemble(window, counts, sentEmails, findPaymentArrivalsAfter(sentEmails));
        ReminderPaymentGoal goal = goalQuery.findPercentageOfRemindersFollowedByPaymentAtLeast().map(ReminderPaymentGoal::new).orElse(null);
        return new CollectionReminderMetric(window.months(), series, goal);
    }

    private List<ReminderDeliveryCount> findDeliveryCounts(MapSqlParameterSource parameters) {
        List<ReminderDeliveryCount> counts = new ArrayList<>();
        jdbc.query("select to_char(scheduled_for, 'YYYY-MM') as month, channel, status, status_reason, kind, count(*) as total from notification_delivery "
                + DELIVERIES_OF_MONTHS + " group by 1, 2, 3, 4, 5", parameters, row -> {
            counts.add(new ReminderDeliveryCount(YearMonth.parse(row.getString("month")), DeliveryChannel.valueOf(row.getString("channel")),
                    DeliveryStatus.valueOf(row.getString("status")), row.getString("status_reason"), row.getString("kind"), row.getLong("total")));
        });
        return counts;
    }

    private List<SentReminderEmail> findSentEmails(MapSqlParameterSource parameters) {
        List<SentReminderEmail> emails = new ArrayList<>();
        jdbc.query("select recipient_person_id, to_char(scheduled_for, 'YYYY-MM') as month, sent_at from notification_delivery "
                + DELIVERIES_OF_MONTHS + " and channel = 'EMAIL' and status in ('SENT', 'SENT_SIMULATED')", parameters, row -> {
            emails.add(new SentReminderEmail(row.getLong("recipient_person_id"), YearMonth.parse(row.getString("month")), row.getTimestamp("sent_at").toInstant()));
        });
        return emails;
    }

    private Map<Long, List<Instant>> findPaymentArrivalsAfter(List<SentReminderEmail> sentEmails) {
        if (sentEmails.isEmpty()) {
            return Map.of();
        }
        Instant earliest = sentEmails.stream().map(SentReminderEmail::sentAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant latest = sentEmails.stream().map(SentReminderEmail::sentAt).max(Comparator.naturalOrder()).orElseThrow();
        List<Long> recipients = sentEmails.stream().map(SentReminderEmail::recipientPersonId).distinct().toList();
        Map<Long, List<Instant>> arrivalsByPerson = new HashMap<>();
        for (PaymentArrival arrival : paymentFacts.findPaymentArrivalsOf(recipients, earliest, latest.plus(ReminderPaymentFollowUp.WINDOW))) {
            arrivalsByPerson.computeIfAbsent(arrival.personId(), person -> new ArrayList<>()).add(arrival.arrivedAt());
        }
        return arrivalsByPerson;
    }
}
