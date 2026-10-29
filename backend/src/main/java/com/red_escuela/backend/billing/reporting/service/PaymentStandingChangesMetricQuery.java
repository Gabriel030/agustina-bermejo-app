package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;

@Component
@Transactional(readOnly = true)
public class PaymentStandingChangesMetricQuery implements MetricQuery {

    static final String BECAME_OVERDUE = "becameOverdue";
    static final String SUSPENDED = "suspended";
    static final String SUSPENSION_LIFTED = "suspensionLifted";
    static final String WITHDRAWN_FOR_NONPAYMENT = "withdrawnForNonpayment";

    private static final String PROGRAM_ENROLLMENT_IN_CURRENCY = "exists (select 1 from program_enrollment pe join program p on p.id = pe.program_id "
            + "where pe.id = s.program_enrollment_id and p.currency = :currency%s)";
    private static final String COURSE_ENROLLMENT_IN_CURRENCY = "exists (select 1 from course_enrollment ce join course c on c.id = ce.course_id "
            + "where ce.id = s.course_enrollment_id and c.currency = :currency%s)";

    private final NamedParameterJdbcTemplate jdbc;

    public PaymentStandingChangesMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.PAYMENT_STANDING_CHANGES;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        if (filter.program().isPresent() && filter.course().isPresent()) {
            return MonthlyMetric.unavailable(panel(), filter.range());
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(filter.range().firstDay()))
                .addValue("to", Date.valueOf(filter.range().dayAfterLastDay()))
                .addValue("currency", filter.currency().name());
        filter.program().ifPresent(programId -> parameters.addValue("programId", programId));
        filter.course().ifPresent(courseId -> parameters.addValue("courseId", courseId));
        String enrollmentScope = filter.program().isPresent() ? PROGRAM_ENROLLMENT_IN_CURRENCY.formatted(" and pe.program_id = :programId")
                : filter.course().isPresent() ? COURSE_ENROLLMENT_IN_CURRENCY.formatted(" and ce.course_id = :courseId")
                : "(" + PROGRAM_ENROLLMENT_IN_CURRENCY.formatted("") + " or " + COURSE_ENROLLMENT_IN_CURRENCY.formatted("") + ")";
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare(BECAME_OVERDUE, MetricUnit.COUNT)
                .declare(SUSPENDED, MetricUnit.COUNT)
                .declare(SUSPENSION_LIFTED, MetricUnit.COUNT)
                .declare(WITHDRAWN_FOR_NONPAYMENT, MetricUnit.COUNT);
        jdbc.query("select to_char(c.business_date, 'YYYY-MM') as month, "
                        + "count(distinct c.standing_id) filter (where c.to_status = 'OVERDUE') as became_overdue, "
                        + "count(distinct c.standing_id) filter (where c.to_status = 'SUSPENDED') as suspended, "
                        + "count(distinct c.standing_id) filter (where c.from_status = 'SUSPENDED' and c.to_status = 'UP_TO_DATE') as suspension_lifted, "
                        + "count(distinct c.standing_id) filter (where c.to_status = 'WITHDRAWN_FOR_NONPAYMENT') as withdrawn "
                        + "from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                        + "where c.event <> 'MIGRATED' and c.business_date >= :from and c.business_date < :to and " + enrollmentScope
                        + " group by 1 order by 1",
                parameters, row -> {
                    var month = MonthlyRows.monthOf(row);
                    series.put(BECAME_OVERDUE, month, new CountValue(row.getLong("became_overdue")));
                    series.put(SUSPENDED, month, new CountValue(row.getLong("suspended")));
                    series.put(SUSPENSION_LIFTED, month, new CountValue(row.getLong("suspension_lifted")));
                    series.put(WITHDRAWN_FOR_NONPAYMENT, month, new CountValue(row.getLong("withdrawn")));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }
}
