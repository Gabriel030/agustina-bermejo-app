package com.red_escuela.backend.billing.simulation;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class Ledger {

    public record Row(String type, YearMonth period, LocalDate issueDate, LocalDate dueDate, BigDecimal total, BigDecimal outstanding, String documentStatus,
                      String paymentStatus) {

        public static Row of(String type, YearMonth period, LocalDate issueDate, LocalDate dueDate, String total, String outstanding, String documentStatus,
                             String paymentStatus) {
            return new Row(type, period, issueDate, dueDate, new BigDecimal(total), new BigDecimal(outstanding), documentStatus, paymentStatus);
        }

        boolean sameAs(Row other) {
            return type.equals(other.type) && period.equals(other.period) && java.util.Objects.equals(issueDate, other.issueDate)
                    && java.util.Objects.equals(dueDate, other.dueDate) && total.compareTo(other.total) == 0 && outstanding.compareTo(other.outstanding) == 0
                    && documentStatus.equals(other.documentStatus) && java.util.Objects.equals(paymentStatus, other.paymentStatus);
        }

        String render() {
            return "%-20s %s issued=%s due=%s total=%s outstanding=%s %s/%s".formatted(type, period, issueDate, dueDate, total.toPlainString(),
                    outstanding.toPlainString(), documentStatus, paymentStatus);
        }
    }

    private final List<Row> rows;

    private Ledger(List<Row> rows) {
        this.rows = rows;
    }

    public static Ledger ofStudent(JdbcTemplate jdbc, Long studentId) {
        List<Row> rows = jdbc.query("""
                select i.invoice_type, (select min(l.period) from invoice_line l where l.invoice_id = i.id) as period, i.issue_date, i.due_date, i.total,
                       i.outstanding_amount, i.document_status, i.payment_status
                from invoice i join student_current_account a on a.id = i.student_current_account_id
                where a.student_id = ?
                order by i.id
                """, (rs, n) -> {
            Date issue = rs.getDate("issue_date");
            Date due = rs.getDate("due_date");
            Date period = rs.getDate("period");
            return new Row(rs.getString("invoice_type"), period == null ? null : YearMonth.from(period.toLocalDate()), issue == null ? null : issue.toLocalDate(),
                    due == null ? null : due.toLocalDate(), rs.getBigDecimal("total"), rs.getBigDecimal("outstanding_amount"), rs.getString("document_status"),
                    rs.getString("payment_status"));
        }, studentId);
        return new Ledger(rows);
    }

    public static Ledger of(List<Row> rows) {
        return new Ledger(List.copyOf(rows));
    }

    public List<Row> rows() {
        return rows;
    }

    public BigDecimal outstandingTotal() {
        return rows.stream().filter(row -> "ISSUED".equals(row.documentStatus())).map(Row::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Ledger hasExactly(Row... expected) {
        assertThat(rows.stream().map(Row::render).toList()).as("ledger rows:%n%s", render()).hasSameSizeAs(expected);
        for (int i = 0; i < expected.length; i++) {
            assertThat(rows.get(i).sameAs(expected[i])).as("row %d%nexpected: %s%n  actual: %s", i, expected[i].render(), rows.get(i).render()).isTrue();
        }
        return this;
    }

    public String render() {
        if (rows.isEmpty()) {
            return "(empty ledger)";
        }
        return String.join("\n", rows.stream().map(Row::render).toList());
    }
}
