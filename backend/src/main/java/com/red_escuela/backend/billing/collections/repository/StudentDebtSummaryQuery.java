package com.red_escuela.backend.billing.collections.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public class StudentDebtSummaryQuery {

    public record StudentDebtRow(Long studentId, BigDecimal totalDue, int installments, int overdueInstallments) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public StudentDebtSummaryQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentDebtRow> findByStudentIds(Collection<Long> studentIds, LocalDate today) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("studentIds", studentIds).addValue("today", Date.valueOf(today));
        return jdbc.query("""
                select a.student_id, sum(i.outstanding_amount) as total_due, count(*) as installments,
                       count(*) filter (where i.payment_status = 'OVERDUE' or i.due_date < :today) as overdue_installments
                from invoice i
                join student_current_account a on a.id = i.student_current_account_id
                where a.student_id in (:studentIds)
                  and i.document_status = 'ISSUED'
                  and i.payment_status in ('PENDING', 'OVERDUE')
                  and i.outstanding_amount > 0
                group by a.student_id
                """, parameters, (row, index) -> new StudentDebtRow(row.getLong("student_id"), row.getBigDecimal("total_due"),
                row.getInt("installments"), row.getInt("overdue_installments")));
    }
}
