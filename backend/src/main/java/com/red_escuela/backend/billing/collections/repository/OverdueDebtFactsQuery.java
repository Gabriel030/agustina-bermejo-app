package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.reporting.domain.OverdueInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class OverdueDebtFactsQuery {

    private static final String OVERDUE_INVOICES_FROM = "from invoice i join student_current_account a on a.id = i.student_current_account_id ";
    private static final String OVERDUE_INVOICES_WHERE = """
            where i.document_status = 'ISSUED'
              and i.payment_status = 'OVERDUE'
              and i.outstanding_amount > 0
              and i.due_date < :businessDate
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OverdueDebtFactsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OverdueInvoiceFact> findOverdueInvoicesOn(LocalDate businessDate) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("businessDate", Date.valueOf(businessDate));
        Map<Long, Set<Long>> programsByInvoice = linksOf("l.invoice_id, pe.program_id as target_id",
                "join invoice_line l on l.invoice_id = i.id join program_enrollment pe on pe.id = l.program_enrollment_id", parameters);
        Map<Long, Set<Long>> coursesByInvoice = linksOf("l.invoice_id, ce.course_id as target_id",
                "join invoice_line l on l.invoice_id = i.id join course_enrollment ce on ce.id = l.course_enrollment_id", parameters);
        List<OverdueInvoiceFact> facts = new ArrayList<>();
        jdbc.query("select i.id, a.student_id, i.due_date, i.currency, i.outstanding_amount " + OVERDUE_INVOICES_FROM + OVERDUE_INVOICES_WHERE + " order by i.id", parameters, row -> {
            Long invoiceId = row.getLong("id");
            facts.add(new OverdueInvoiceFact(invoiceId, row.getLong("student_id"), row.getDate("due_date").toLocalDate(),
                    Money.of(row.getBigDecimal("outstanding_amount"), Currency.valueOf(row.getString("currency"))),
                    programsByInvoice.getOrDefault(invoiceId, Set.of()), coursesByInvoice.getOrDefault(invoiceId, Set.of())));
        });
        return facts;
    }

    public Map<Currency, Money> findUnallocatedCreditByCurrency() {
        Map<Currency, Money> credit = new HashMap<>();
        jdbc.query("select currency, coalesce(sum(unallocated_credit_amount), 0) as amount from student_current_account group by currency", row -> {
            Currency currency = Currency.valueOf(row.getString("currency"));
            credit.put(currency, Money.of(row.getBigDecimal("amount"), currency));
        });
        return credit;
    }

    private Map<Long, Set<Long>> linksOf(String columns, String joins, MapSqlParameterSource parameters) {
        Map<Long, Set<Long>> targetsByInvoice = new HashMap<>();
        jdbc.query("select distinct " + columns + " " + OVERDUE_INVOICES_FROM + joins + " " + OVERDUE_INVOICES_WHERE, parameters, row -> {
                    targetsByInvoice.computeIfAbsent(row.getLong("invoice_id"), id -> new HashSet<>()).add(row.getLong("target_id"));
                });
        return targetsByInvoice;
    }
}
