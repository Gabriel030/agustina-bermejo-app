package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class DebtorPageReader {

    private static final String DEBT_AMOUNT_SORT = "DEBT_AMOUNT";
    private static final String ORDER_BY_AMOUNT = " order by g.currency, g.total_debt desc, g.student_id";
    private static final String ORDER_BY_AGE = " order by (cast(:today as date) - g.oldest_due_date) desc nulls last, g.student_id, g.currency";

    private static final String DEBTS_BY_STUDENT = """
            with overdue as (
                select i.id as invoice_id, i.currency, i.due_date, greatest(i.outstanding_amount, 0) as outstanding,
                       (select l.course_enrollment_id from invoice_line l
                         where l.invoice_id = i.id and l.course_enrollment_id is not null order by l.line_number limit 1) as course_enrollment_id,
                       (select l.program_enrollment_id from invoice_line l
                         where l.invoice_id = i.id and l.program_enrollment_id is not null order by l.line_number limit 1) as program_enrollment_id
                from invoice i
                where i.payment_status = 'OVERDUE'
            ), resolved as (
                select o.invoice_id, o.currency, o.due_date, o.outstanding,
                       coalesce(ce.student_id, pe.student_id) as student_id,
                       case when ce.id is not null then c.name else p.name end as enrollment_name
                from overdue o
                left join course_enrollment ce on ce.id = o.course_enrollment_id
                left join course c on c.id = ce.course_id
                left join program_enrollment pe on pe.id = o.program_enrollment_id and ce.id is null
                left join program p on p.id = pe.program_id
                where ce.id is not null or pe.id is not null
            ), grouped as (
                select r.student_id, r.currency, sum(r.outstanding) as total_debt, min(r.due_date) as oldest_due_date,
                       (array_agg(r.enrollment_name order by r.due_date, r.invoice_id))[1] as enrollment_name
                from resolved r
                group by r.student_id, r.currency
            )
            """;

    private static final String PAGE_QUERY = DEBTS_BY_STUDENT + """
            select g.student_id, g.currency, g.total_debt, g.enrollment_name,
                   (cast(:today as date) - g.oldest_due_date) as debt_age_days,
                   per.name as person_name, per.last_name, per.dni, count(*) over () as total_elements
            from grouped g
            join student s on s.id = g.student_id
            join person per on per.id = s.person_id
            """;

    private static final String COUNT_QUERY = DEBTS_BY_STUDENT + """
            select count(*) from grouped g
            join student s on s.id = g.student_id
            join person per on per.id = s.person_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DebtorPageReader(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PageResponse<DebtorSummaryResponse> findDebtorsPage(String sortBy, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        long offset = (long) boundedPage * boundedSize;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("today", Date.valueOf(LocalDate.now(clock)))
                .addValue("limit", boundedSize)
                .addValue("offset", offset);
        String orderBy = DEBT_AMOUNT_SORT.equals(sortBy) ? ORDER_BY_AMOUNT : ORDER_BY_AGE;
        long[] total = {0};
        List<DebtorSummaryResponse> items = jdbc.query(PAGE_QUERY + orderBy + " limit :limit offset :offset", parameters, (rs, rowNumber) -> {
            total[0] = rs.getLong("total_elements");
            Currency currency = Currency.valueOf(rs.getString("currency"));
            int debtAgeDays = rs.getObject("debt_age_days") == null ? 0 : rs.getInt("debt_age_days");
            return new DebtorSummaryResponse(rs.getLong("student_id"), rs.getString("person_name") + " " + rs.getString("last_name"), rs.getString("dni"),
                    rs.getString("enrollment_name"), Money.of(rs.getBigDecimal("total_debt"), currency), debtAgeDays);
        });
        if (items.isEmpty() && offset > 0) {
            total[0] = jdbc.queryForObject(COUNT_QUERY, parameters, Long.class);
        }
        return new PageResponse<>(items, boundedPage, boundedSize, total[0]);
    }
}
