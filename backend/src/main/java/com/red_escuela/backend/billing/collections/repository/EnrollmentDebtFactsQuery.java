package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.EnrollmentDebtFacts;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class EnrollmentDebtFactsQuery {

    public record InstallmentDueDate(Long invoiceId, LocalDate dueDate) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public EnrollmentDebtFactsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EnrollmentDebtFacts findLiveFactsOf(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate, LocalDate protectionFloor) {
        int[] counts = new int[4];
        jdbc.query("""
                select i.invoice_type, count(distinct i.id) as invoices
                from invoice i
                join invoice_line l on l.invoice_id = i.id
                where l.%s = :enrollmentId
                  and i.document_status = 'ISSUED'
                  and (i.payment_status = 'OVERDUE'
                       or (i.payment_status = 'PAYMENT_UNDER_REVIEW'
                           and i.due_date < :businessDate
                           and exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW' and cast(r.uploaded_at as date) < :protectionFloor)
                           and not exists (select 1 from bank_transfer_receipt r where r.invoice_id = i.id and r.status = 'PENDING_REVIEW' and cast(r.uploaded_at as date) >= :protectionFloor)))
                  and i.outstanding_amount > 0
                group by i.invoice_type
                """.formatted(enrollmentColumn(kind)),
                new MapSqlParameterSource("enrollmentId", enrollmentId).addValue("businessDate", Date.valueOf(businessDate)).addValue("protectionFloor", Date.valueOf(protectionFloor)), row -> {
            int invoices = row.getInt("invoices");
            switch (row.getString("invoice_type")) {
                case "MONTHLY_INSTALLMENT" -> counts[0] += invoices;
                case "ENROLLMENT_FEE" -> counts[1] += invoices;
                case "LATE_FEE" -> counts[2] += invoices;
                default -> counts[3] += invoices;
            }
        });
        return new EnrollmentDebtFacts(counts[0], counts[1], counts[2], counts[3]);
    }

    public List<InstallmentDueDate> findMonthlyInstallmentsUnpaidAndUnprotectedAtCutoff(EnrollmentKind kind, Long enrollmentId, LocalDate cutoff, LocalDate protectionFloor) {
        List<InstallmentDueDate> installments = new ArrayList<>();
        jdbc.query("""
                select distinct i.id, i.due_date
                from invoice i
                join invoice_line l on l.invoice_id = i.id
                where l.%s = :enrollmentId
                  and i.document_type = 'INVOICE'
                  and i.invoice_type = 'MONTHLY_INSTALLMENT'
                  and i.document_status = 'ISSUED'
                  and i.due_date < :cutoff
                  and i.total
                      - coalesce((select sum(case when a.reverses_allocation_id is null then a.amount else -a.amount end)
                                  from payment_allocation a where a.invoice_id = i.id
                                    and (case when a.reverses_allocation_id is null then a.effective_date else a.allocated_on end) <= :cutoff), 0)
                      - coalesce((select sum(n.offset_amount) from credit_note n where n.invoice_id = i.id and n.issue_date <= :cutoff), 0) > 0
                  and not exists (select 1
                                  from payment_checkout_item item
                                  join payment p on p.id = item.payment_id
                                  where item.invoice_id = i.id
                                    and item.marked_under_review
                                    and p.method_type = 'BANK_TRANSFER'
                                    and cast(p.registered_at as date) <= i.due_date
                                    and cast(p.registered_at as date) <= :cutoff
                                    and cast(p.registered_at as date) >= :protectionFloor
                                    and not exists (select 1 from payment_status_change change
                                                    where change.payment_id = p.id
                                                      and change.to_status <> 'PENDING'
                                                      and cast(change.occurred_at as date) <= :cutoff))
                order by i.due_date, i.id
                """.formatted(enrollmentColumn(kind)),
                new MapSqlParameterSource("enrollmentId", enrollmentId).addValue("cutoff", Date.valueOf(cutoff)).addValue("protectionFloor", Date.valueOf(protectionFloor)),
                row -> {
                    installments.add(new InstallmentDueDate(row.getLong("id"), row.getDate("due_date").toLocalDate()));
                });
        return installments;
    }

    public boolean isEnrollmentActive(EnrollmentKind kind, Long enrollmentId) {
        String sql = kind == EnrollmentKind.PROGRAM
                ? "select count(*) from program_enrollment where id = :id and status in ('ENROLLED', 'PAUSED')"
                : "select count(*) from course_enrollment where id = :id and status = 'ENROLLED'";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", enrollmentId), Long.class) > 0;
    }

    public boolean isEnrollmentWithdrawnForNonpayment(EnrollmentKind kind, Long enrollmentId) {
        String table = kind == EnrollmentKind.PROGRAM ? "program_enrollment" : "course_enrollment";
        return jdbc.queryForObject("select count(*) from " + table + " where id = :id and status = 'WITHDRAWN' and withdrawn_by_person_id is null",
                new MapSqlParameterSource("id", enrollmentId), Long.class) > 0;
    }

    public Optional<Long> findStudentIdOf(EnrollmentKind kind, Long enrollmentId) {
        String table = kind == EnrollmentKind.PROGRAM ? "program_enrollment" : "course_enrollment";
        List<Long> students = jdbc.queryForList("select student_id from " + table + " where id = :id and student_id is not null",
                new MapSqlParameterSource("id", enrollmentId), Long.class);
        return students.stream().findFirst();
    }

    public List<Long> findStepCandidateEnrollmentIds(EnrollmentKind kind) {
        String column = enrollmentColumn(kind);
        String sql = kind == EnrollmentKind.PROGRAM
                ? "select id from program_enrollment where status in ('ENROLLED', 'PAUSED')"
                : "select id from course_enrollment where status = 'ENROLLED'";
        return new ArrayList<>(jdbc.queryForList("select id from (" + sql + " union select " + column + " from enrollment_payment_standing "
                        + "where " + column + " is not null and status in ('OVERDUE', 'SUSPENDED')) candidates order by id",
                new MapSqlParameterSource(), Long.class));
    }

    public List<Long> findEnrollmentIdsOfInvoice(EnrollmentKind kind, Long invoiceId) {
        String column = enrollmentColumn(kind);
        return jdbc.queryForList("select distinct %s from invoice_line where invoice_id = :invoiceId and %s is not null".formatted(column, column),
                new MapSqlParameterSource("invoiceId", invoiceId), Long.class);
    }

    private static String enrollmentColumn(EnrollmentKind kind) {
        return kind == EnrollmentKind.PROGRAM ? "program_enrollment_id" : "course_enrollment_id";
    }
}
