package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostWithdrawalSummaryStore {

    private static final String DEBT_LINES_SQL = """
            select distinct e.student_id, s.person_id, e.id as enrollment_id, e.withdrawal_effective_date, i.id as invoice_id,
                   i.invoice_type, i.due_date, i.currency, i.outstanding_amount
            from %1$s e
            join student s on s.id = e.student_id
            join invoice_line l on l.%2$s = e.id
            join invoice i on i.id = l.invoice_id
            where e.status = 'WITHDRAWN'
              and e.withdrawn_by_person_id is not null
              and e.withdrawal_effective_date is not null
              and e.withdrawal_effective_date < :summaryMonth
              and i.document_status = 'ISSUED'
              and i.document_type = 'INVOICE'
              and i.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
              and i.payment_status in ('PENDING', 'OVERDUE')
              and i.due_date < :summaryDay
              and not exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW')
              and not exists (select 1 from post_withdrawal_summary w where w.%2$s = e.id and w.summary_month = :summaryMonth)
            order by e.student_id, e.id, i.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostWithdrawalSummaryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WithdrawnEnrollmentDebtLine> findPendingDebtLinesOfWithdrawnEnrollments(LocalDate businessDate, LocalDate summaryDay) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("summaryDay", Date.valueOf(summaryDay))
                .addValue("summaryMonth", Date.valueOf(YearMonth.from(businessDate).atDay(1)));
        List<WithdrawnEnrollmentDebtLine> lines = new ArrayList<>(jdbc.query(DEBT_LINES_SQL.formatted("program_enrollment", "program_enrollment_id"), parameters,
                (rs, rowNumber) -> lineOf(rs, true)));
        lines.addAll(jdbc.query(DEBT_LINES_SQL.formatted("course_enrollment", "course_enrollment_id"), parameters, (rs, rowNumber) -> lineOf(rs, false)));
        return lines;
    }

    public void recordSummaryOf(Long studentId, Long programEnrollmentId, Long courseEnrollmentId, LocalDate businessDate) {
        jdbc.update("""
                insert into post_withdrawal_summary (student_id, program_enrollment_id, course_enrollment_id, summary_month, business_date, created_at)
                values (:studentId, :programEnrollmentId, :courseEnrollmentId, :summaryMonth, :businessDate, :createdAt)
                """, new MapSqlParameterSource().addValue("studentId", studentId).addValue("programEnrollmentId", programEnrollmentId)
                .addValue("courseEnrollmentId", courseEnrollmentId).addValue("summaryMonth", Date.valueOf(YearMonth.from(businessDate).atDay(1)))
                .addValue("businessDate", Date.valueOf(businessDate)).addValue("createdAt", Timestamp.valueOf(ApplicationTime.now())));
    }

    private static WithdrawnEnrollmentDebtLine lineOf(ResultSet rs, boolean programEnrollment) throws SQLException {
        Long enrollmentId = rs.getLong("enrollment_id");
        Currency currency = Currency.valueOf(rs.getString("currency"));
        return new WithdrawnEnrollmentDebtLine(rs.getLong("student_id"), rs.getLong("person_id"), programEnrollment ? enrollmentId : null,
                programEnrollment ? null : enrollmentId, rs.getDate("withdrawal_effective_date").toLocalDate(), rs.getLong("invoice_id"),
                "ENROLLMENT_FEE".equals(rs.getString("invoice_type")), rs.getDate("due_date").toLocalDate(),
                Money.of(rs.getBigDecimal("outstanding_amount"), currency));
    }
}
