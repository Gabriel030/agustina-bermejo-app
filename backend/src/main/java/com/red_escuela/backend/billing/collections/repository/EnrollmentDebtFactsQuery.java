package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.EnrollmentDebtFacts;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class EnrollmentDebtFactsQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public EnrollmentDebtFactsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EnrollmentDebtFacts findFactsOf(EnrollmentKind kind, Long enrollmentId, LocalDate monthCloseCutoff) {
        String enrollmentColumn = kind == EnrollmentKind.PROGRAM ? "program_enrollment_id" : "course_enrollment_id";
        int[] counts = new int[4];
        LocalDate[] oldestInstallment = new LocalDate[1];
        jdbc.query("""
                select i.invoice_type, count(distinct i.id) as invoices, min(i.due_date) as oldest_due_date
                from invoice i
                join invoice_line l on l.invoice_id = i.id
                where l.%s = :enrollmentId
                  and i.document_status = 'ISSUED'
                  and i.payment_status = 'OVERDUE'
                  and i.outstanding_amount > 0
                group by i.invoice_type
                """.formatted(enrollmentColumn), new MapSqlParameterSource("enrollmentId", enrollmentId), row -> {
            int invoices = row.getInt("invoices");
            switch (row.getString("invoice_type")) {
                case "MONTHLY_INSTALLMENT" -> {
                    counts[0] += invoices;
                    oldestInstallment[0] = row.getDate("oldest_due_date").toLocalDate();
                }
                case "ENROLLMENT_FEE" -> counts[1] += invoices;
                case "LATE_FEE" -> counts[2] += invoices;
                default -> counts[3] += invoices;
            }
        });
        return new EnrollmentDebtFacts(counts[0], oldestInstallment[0], counts[1], counts[2], counts[3], monthCloseCutoff);
    }

    public Optional<Long> findStudentIdOf(EnrollmentKind kind, Long enrollmentId) {
        String table = kind == EnrollmentKind.PROGRAM ? "program_enrollment" : "course_enrollment";
        List<Long> students = jdbc.queryForList("select student_id from " + table + " where id = :id and student_id is not null",
                new MapSqlParameterSource("id", enrollmentId), Long.class);
        return students.stream().findFirst();
    }

    public List<Long> findActiveEnrollmentIds(EnrollmentKind kind) {
        String sql = kind == EnrollmentKind.PROGRAM
                ? "select id from program_enrollment where status in ('ENROLLED', 'PAUSED') order by id"
                : "select id from course_enrollment where status = 'ENROLLED' order by id";
        return new ArrayList<>(jdbc.queryForList(sql, new MapSqlParameterSource(), Long.class));
    }

    public List<Long> findEnrollmentIdsOfInvoice(EnrollmentKind kind, Long invoiceId) {
        String enrollmentColumn = kind == EnrollmentKind.PROGRAM ? "program_enrollment_id" : "course_enrollment_id";
        return jdbc.queryForList("select distinct %s from invoice_line where invoice_id = :invoiceId and %s is not null".formatted(enrollmentColumn, enrollmentColumn),
                new MapSqlParameterSource("invoiceId", invoiceId), Long.class);
    }
}
