package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractMonthlyMetricsIT extends AbstractCreditNoteIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<Long> seededInvoiceIds = new ArrayList<>();

    protected Long arsAccountId;
    protected Long usdAccountId;
    protected String directorToken;

    @BeforeEach
    void openAccountsAndSignInAsDirector() {
        Long studentId = newStudentId();
        arsAccountId = newAccountId(studentId, Currency.ARS);
        usdAccountId = newAccountId(studentId, Currency.USD);
        directorToken = newStaff("DIRECTOR").token();
    }

    @AfterEach
    void deleteSeededRows() {
        if (seededInvoiceIds.isEmpty()) {
            return;
        }
        String ids = seededInvoiceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<String> guards = List.of("payment_allocation.trg_payment_allocation_append_only", "invoice_status_change.trg_invoice_status_change_append_only",
                "invoice_line.trg_invoice_line_immutable_when_issued", "invoice_discount.trg_invoice_discount_immutable_when_issued");
        guards.forEach(guard -> jdbc.execute("alter table " + guard.split("\\.")[0] + " disable trigger " + guard.split("\\.")[1]));
        try {
            jdbc.update("delete from bank_transfer_receipt where invoice_id in (" + ids + ")");
            jdbc.update("delete from payment_allocation where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice_status_change where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id in (" + ids + "))");
            jdbc.update("delete from invoice_line where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice where id in (" + ids + ")");
        } finally {
            guards.forEach(guard -> jdbc.execute("alter table " + guard.split("\\.")[0] + " enable trigger " + guard.split("\\.")[1]));
            seededInvoiceIds.clear();
        }
    }

    protected Long seedInvoice(Long accountId, Currency currency, InvoiceType type, InvoiceIssuingReason reason, LocalDate issued, LocalDate due,
                               String paymentStatus, String lines, String discount, String discountType) {
        BigDecimal linesTotal = new BigDecimal(lines);
        BigDecimal discountTotal = new BigDecimal(discount);
        BigDecimal total = linesTotal.subtract(discountTotal);
        BigDecimal paid = paymentStatus.equals("PAID") ? total : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid);
        String code = currency.name();
        String number = String.format("F-%08d", jdbc.queryForObject("select nextval('invoice_number_seq')", Long.class));
        Long invoiceId = jdbc.queryForObject("insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, "
                        + "document_status, payment_status, invoice_number, issue_date, due_date, student_snapshot, lines_total, lines_total_currency, "
                        + "discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency, credited_amount, "
                        + "credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at) "
                        + "values (?, ?, 'INVOICE', ?, ?, 'ISSUED', ?, ?, ?, ?, cast('{}' as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, now(), now()) returning id",
                Long.class, accountId, code, type.name(), reason.name(), paymentStatus, number, issued, due, linesTotal, code, discountTotal, code,
                total, code, paid, code, code, outstanding, code);
        Long lineId = jdbc.queryForObject("insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, "
                        + "amount_currency, discount_amount, discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount_currency) "
                        + "values (?, ?, 1, 'ADMINISTRATIVE_FEE', 'Concepto', ?, ?, ?, ?, ?, ?, 0, ?, ?) returning id",
                Long.class, invoiceId, code, issued, "K" + number, linesTotal, code, discountTotal, code, code, code);
        seededInvoiceIds.add(invoiceId);
        if (discountType != null) {
            jdbc.update("insert into invoice_discount (invoice_line_id, discount_type, concept, currency, base_amount, base_amount_currency, "
                            + "discount_amount, discount_amount_currency) values (?, ?, 'Descuento', ?, ?, ?, ?, ?)",
                    lineId, discountType, code, linesTotal, code, discountTotal, code);
        }
        return invoiceId;
    }

    protected void seedAllocation(Long accountId, Long invoiceId, Currency currency, String amount, LocalDate effectiveDate) {
        jdbc.update("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, effective_date, allocated_on, "
                        + "actor_process, recorded_at) values (?, ?, ?, ?, ?, ?, 'TEST', now())",
                accountId, invoiceId, new BigDecimal(amount), currency.name(), effectiveDate, effectiveDate);
    }

    protected void seedOverdueDetection(Long invoiceId, LocalDate businessDate, String occurredAt) {
        jdbc.update("insert into invoice_status_change (invoice_id, status_kind, from_status, to_status, event, actor_process, business_date, occurred_at) "
                        + "values (?, 'PAYMENT', 'PENDING', 'OVERDUE', 'DUE_DATE_PASSED', 'TEST', ?, cast(? as timestamp))",
                invoiceId, businessDate, occurredAt);
    }

    protected JsonNode metric(String token, String panel, String query) throws Exception {
        String body = mockMvc.perform(get("/billing/metrics/" + panel + "?" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    protected JsonNode errorOf(String token, String path, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    protected static JsonNode seriesOf(JsonNode metric, String key) {
        for (JsonNode series : metric.get("series")) {
            if (series.get("key").asText().equals(key)) {
                return series;
            }
        }
        throw new AssertionError("The panel has no series " + key + ": " + metric);
    }

    protected static boolean hasSeries(JsonNode metric, String key) {
        for (JsonNode series : metric.get("series")) {
            if (series.get("key").asText().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
