package com.red_escuela.backend.billing.collections.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public class OverdueContactQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public OverdueContactQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OverdueAccountRow> findOverdueAccountsAsOf(LocalDate asOf) {
        return jdbc.query("""
                select a.student_id, i.currency, count(*) as overdue_installments, max(cast(:asOf as date) - i.due_date) as oldest_days_overdue,
                       sum(i.outstanding_amount) as overdue_amount
                from invoice i join student_current_account a on a.id = i.student_current_account_id
                where i.document_status = 'ISSUED'
                  and i.document_type = 'INVOICE'
                  and i.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
                  and i.payment_status in ('PENDING', 'OVERDUE')
                  and i.due_date < :asOf
                  and i.outstanding_amount > 0
                  and not exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW')
                group by a.student_id, i.currency
                order by a.student_id, i.currency
                """, new MapSqlParameterSource().addValue("asOf", asOf),
                (rs, rowNumber) -> new OverdueAccountRow(rs.getLong("student_id"), rs.getString("currency"), rs.getInt("overdue_installments"),
                        rs.getInt("oldest_days_overdue"), rs.getBigDecimal("overdue_amount")));
    }

    public List<StudentIdentityRow> findIdentitiesOf(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select s.id, p.name, p.last_name, p.dni
                from student s join person p on p.id = s.person_id
                where s.id in (:studentIds)
                """, new MapSqlParameterSource().addValue("studentIds", studentIds),
                (rs, rowNumber) -> new StudentIdentityRow(rs.getLong("id"), rs.getString("name"), rs.getString("last_name"), rs.getString("dni")));
    }

    public List<LastReminderRow> findLastReminderDatesOf(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select student_id, max(business_date) as last_reminder_date
                from collection_reminder
                where student_id in (:studentIds)
                  and (jsonb_array_length(in_app_messages) > 0 or jsonb_array_length(email_messages) > 0)
                group by student_id
                """, new MapSqlParameterSource().addValue("studentIds", studentIds),
                (rs, rowNumber) -> new LastReminderRow(rs.getLong("student_id"), rs.getObject("last_reminder_date", LocalDate.class)));
    }

    public record OverdueAccountRow(Long studentId, String currency, int overdueInstallments, int oldestDaysOverdue, BigDecimal overdueAmount) {
    }

    public record StudentIdentityRow(Long studentId, String name, String lastName, String dni) {
    }

    public record LastReminderRow(Long studentId, LocalDate lastReminderDate) {
    }
}
