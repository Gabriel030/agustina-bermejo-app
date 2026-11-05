package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.api.PaymentArrival;
import com.red_escuela.backend.billing.api.PaymentFactsQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StoredPaymentFactsQuery implements PaymentFactsQuery {

    static final int PERSONS_PER_STATEMENT = 1000;

    private static final String APPROVALS_OF_PERSONS = """
            select s.person_id, c.occurred_at
            from payment p
            join payment_status_change c on c.payment_id = p.id and c.to_status = 'APPROVED'
            join student_current_account a on a.id = p.student_current_account_id
            join student s on s.id = a.student_id
            where s.person_id in (:personIds)
              and p.status in ('APPROVED', 'REFUNDED')
              and c.occurred_at >= :from and c.occurred_at <= :until
            order by s.person_id, c.occurred_at""";

    private final NamedParameterJdbcTemplate jdbc;

    public StoredPaymentFactsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaymentArrival> findPaymentArrivalsOf(Collection<Long> personIds, Instant from, Instant until) {
        ZoneId zone = ZoneId.systemDefault();
        List<Long> distinctPersonIds = personIds.stream().distinct().toList();
        List<PaymentArrival> arrivals = new ArrayList<>();
        for (int start = 0; start < distinctPersonIds.size(); start += PERSONS_PER_STATEMENT) {
            List<Long> chunk = distinctPersonIds.subList(start, Math.min(start + PERSONS_PER_STATEMENT, distinctPersonIds.size()));
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("personIds", chunk)
                    .addValue("from", Timestamp.valueOf(LocalDateTime.ofInstant(from, zone)))
                    .addValue("until", Timestamp.valueOf(LocalDateTime.ofInstant(until, zone)));
            jdbc.query(APPROVALS_OF_PERSONS, parameters, row -> {
                arrivals.add(new PaymentArrival(row.getLong("person_id"), row.getTimestamp("occurred_at").toLocalDateTime().atZone(zone).toInstant()));
            });
        }
        return List.copyOf(arrivals);
    }
}
