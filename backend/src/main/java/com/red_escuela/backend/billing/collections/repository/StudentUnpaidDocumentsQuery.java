package com.red_escuela.backend.billing.collections.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class StudentUnpaidDocumentsQuery {

    public record UnprotectedUnpaidDocument(Long invoiceId, LocalDate dueDate, boolean markedOverdue) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public StudentUnpaidDocumentsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UnprotectedUnpaidDocument> findUnprotectedDueBefore(Long studentId, LocalDate today, LocalDate protectionFloor) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("studentId", studentId)
                .addValue("today", Date.valueOf(today)).addValue("protectionFloor", Date.valueOf(protectionFloor));
        return jdbc.query("""
                select i.id, i.due_date, i.payment_status
                from invoice i
                join student_current_account a on a.id = i.student_current_account_id
                where a.student_id = :studentId
                  and i.document_status = 'ISSUED'
                  and i.payment_status in ('PENDING', 'OVERDUE', 'PAYMENT_UNDER_REVIEW')
                  and i.outstanding_amount > 0
                  and i.due_date < :today
                  and not (i.payment_status = 'PAYMENT_UNDER_REVIEW'
                           and exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW'
                                       and cast(r.uploaded_at as date) <= i.due_date)
                           and not (exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW'
                                            and cast(r.uploaded_at as date) < :protectionFloor)
                                    and not exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW'
                                                    and cast(r.uploaded_at as date) >= :protectionFloor)))
                order by i.id
                """, parameters, (row, index) -> new UnprotectedUnpaidDocument(row.getLong("id"), row.getDate("due_date").toLocalDate(),
                "OVERDUE".equals(row.getString("payment_status"))));
    }
}
