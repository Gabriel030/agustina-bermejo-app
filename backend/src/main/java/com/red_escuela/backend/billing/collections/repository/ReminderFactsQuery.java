package com.red_escuela.backend.billing.collections.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public class ReminderFactsQuery {

    private static final String REMINDABLE_INVOICES_OF_STUDENTS = """
            from invoice i join student_current_account a on a.id = i.student_current_account_id
            where i.document_status = 'ISSUED'
              and i.payment_status in ('PENDING', 'OVERDUE', 'PAYMENT_UNDER_REVIEW')
              and i.document_type = 'INVOICE'
              and i.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReminderFactsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findStudentIdsWithRemindableInvoicesAfter(Long afterStudentId, int limit) {
        return jdbc.queryForList("select distinct a.student_id " + REMINDABLE_INVOICES_OF_STUDENTS + " and a.student_id > :after order by a.student_id limit :limit",
                new MapSqlParameterSource().addValue("after", afterStudentId).addValue("limit", limit), Long.class);
    }

    public List<Long> findStudentIdsWithRemindableInvoicesAmong(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("select distinct a.student_id " + REMINDABLE_INVOICES_OF_STUDENTS + " and a.student_id in (:studentIds) order by a.student_id",
                new MapSqlParameterSource().addValue("studentIds", studentIds), Long.class);
    }

    public List<OpenInvoiceRow> findOpenInvoicesOf(Collection<Long> studentIds, LocalDate previousBusinessDate, LocalDateTime previousPlanningRun, LocalDate protectionFloor) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return queryOpenInvoices("a.student_id in (:studentIds)", "order by a.student_id, i.id",
                new MapSqlParameterSource().addValue("studentIds", studentIds), previousBusinessDate, previousPlanningRun, protectionFloor);
    }

    public List<OpenInvoiceRow> findOpenInvoicesByIds(Collection<Long> invoiceIds, LocalDate businessDate, LocalDate protectionFloor) {
        if (invoiceIds.isEmpty()) {
            return List.of();
        }
        return queryOpenInvoices("i.id in (:invoiceIds)", "order by i.id", new MapSqlParameterSource().addValue("invoiceIds", invoiceIds),
                businessDate.minusDays(1), businessDate.minusDays(1).atStartOfDay(), protectionFloor);
    }

    private List<OpenInvoiceRow> queryOpenInvoices(String scope, String ordering, MapSqlParameterSource scopeParameters, LocalDate previousBusinessDate,
                                                   LocalDateTime previousPlanningRun, LocalDate protectionFloor) {
        return jdbc.query("""
                select a.student_id, i.id, i.origin_invoice_id, i.document_type, i.invoice_type, i.document_status,
                       case when i.payment_status = 'PAYMENT_UNDER_REVIEW' and exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW'
                                                                                     and cast(r.uploaded_at as date) < :protectionFloor)
                                 and not exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW' and cast(r.uploaded_at as date) >= :protectionFloor)
                            then 'PENDING' else i.payment_status end as payment_status, i.issue_date, i.due_date,
                       i.currency, i.outstanding_amount,
                       (select l.amount from invoice_line l where l.invoice_id = i.id order by l.id limit 1) as final_price,
                       (select min(l.program_enrollment_id) from invoice_line l where l.invoice_id = i.id) as program_enrollment_id,
                       (select min(l.course_enrollment_id) from invoice_line l where l.invoice_id = i.id) as course_enrollment_id,
                       exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW' and cast(r.uploaded_at as date) >= :protectionFloor) as receipt_under_review,
                       exists (select 1 from authorization_request_invoice ari join authorization_request ar on ar.id = ari.authorization_request_id
                               where ari.invoice_id = i.id and ar.status = 'PENDING') as open_cancellation_request,
                       (i.issue_date = :previousBusinessDate and exists (select 1 from invoice_status_change c where c.invoice_id = i.id and c.status_kind = 'DOCUMENT'
                               and c.to_status = 'ISSUED' and c.occurred_at > :previousPlanningRun)) as issued_after_previous_run
                from invoice i join student_current_account a on a.id = i.student_current_account_id
                where %s
                  and i.document_status = 'ISSUED'
                  and i.payment_status in ('PENDING', 'OVERDUE', 'PAYMENT_UNDER_REVIEW')
                %s
                """.formatted(scope, ordering),
                scopeParameters.addValue("previousBusinessDate", previousBusinessDate).addValue("previousPlanningRun", Timestamp.valueOf(previousPlanningRun))
                        .addValue("protectionFloor", Date.valueOf(protectionFloor)),
                (rs, rowNumber) -> new OpenInvoiceRow(rs.getLong("student_id"), rs.getLong("id"), (Long) rs.getObject("origin_invoice_id"), rs.getString("document_type"),
                        rs.getString("invoice_type"), rs.getString("document_status"), rs.getString("payment_status"), rs.getObject("issue_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class), rs.getString("currency"), rs.getBigDecimal("outstanding_amount"),
                        (Long) rs.getObject("program_enrollment_id"), (Long) rs.getObject("course_enrollment_id"), rs.getBoolean("receipt_under_review"),
                        rs.getBoolean("open_cancellation_request"), rs.getBoolean("issued_after_previous_run"), rs.getBigDecimal("final_price")));
    }

    public List<CreditRow> findUnallocatedCreditOf(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select student_id, currency, unallocated_credit_amount from student_current_account where student_id in (:studentIds)",
                new MapSqlParameterSource().addValue("studentIds", studentIds),
                (rs, rowNumber) -> new CreditRow(rs.getLong("student_id"), rs.getString("currency"), rs.getBigDecimal("unallocated_credit_amount")));
    }

    public List<StudentPersonRow> findPersonsOf(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select id, person_id from student where id in (:studentIds)", new MapSqlParameterSource().addValue("studentIds", studentIds),
                (rs, rowNumber) -> new StudentPersonRow(rs.getLong("id"), rs.getLong("person_id")));
    }

    public record OpenInvoiceRow(Long studentId, Long invoiceId, Long originInvoiceId, String documentType, String invoiceType, String documentStatus, String paymentStatus,
                                 LocalDate issueDate, LocalDate dueDate, String currency, BigDecimal outstandingAmount, Long programEnrollmentId, Long courseEnrollmentId,
                                 boolean receiptUnderReview, boolean openCancellationRequest, boolean issuedAfterPreviousRun, BigDecimal finalPrice) {
    }

    public record CreditRow(Long studentId, String currency, BigDecimal amount) {
    }

    public record StudentPersonRow(Long studentId, Long personId) {
    }
}
