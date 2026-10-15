package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
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
    private final List<Long> seededPriceIds = new ArrayList<>();
    private final List<Long> seededProgramIds = new ArrayList<>();
    private final List<Long> seededConfigurationVersionIds = new ArrayList<>();

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
        List<String> guards = List.of("payment_allocation_source.trg_payment_allocation_source_append_only", "payment_allocation.trg_payment_allocation_append_only", "invoice_status_change.trg_invoice_status_change_append_only",
                "invoice_line.trg_invoice_line_immutable_when_issued", "invoice_discount.trg_invoice_discount_immutable_when_issued");
        guards.forEach(guard -> jdbc.execute("alter table " + guard.split("\\.")[0] + " disable trigger " + guard.split("\\.")[1]));
        try {
            jdbc.update("delete from late_fee_charge where debit_note_id in (" + ids + ") or origin_invoice_id in (" + ids + ")");
            jdbc.update("delete from bank_transfer_receipt where invoice_id in (" + ids + ")");
            jdbc.update("delete from payment_allocation_source where payment_allocation_id in (select id from payment_allocation where invoice_id in (" + ids + "))");
            jdbc.update("delete from payment_allocation where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice_status_change where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id in (" + ids + "))");
            jdbc.update("delete from invoice_line where invoice_id in (" + ids + ")");
            jdbc.update("delete from invoice where id in (" + ids + ")");
            seededConfigurationVersionIds.forEach(versionId -> jdbc.update("delete from configuration_version where id = ?", versionId));
            seededConfigurationVersionIds.clear();
            seededPriceIds.forEach(priceId -> {
                jdbc.update("delete from price_version_status_change where price_version_id in (select id from price_version where price_id = ?)", priceId);
                jdbc.update("delete from price_version where price_id = ?", priceId);
                jdbc.update("delete from price where id = ?", priceId);
            });
            seededPriceIds.clear();
            seededProgramIds.forEach(programId -> jdbc.update("delete from program where id = ?", programId));
            seededProgramIds.clear();
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

    protected Long seedLateFee(Long accountId, Long originInvoiceId, Currency currency, int monthOrdinal, LocalDate factDate, LocalDate issued, String amount) {
        Long debitNoteId = seedInvoice(accountId, currency, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.LATE_FEE, issued, issued, "PENDING", amount, "0.00", null);
        jdbc.update("update invoice set document_type = 'DEBIT_NOTE', invoice_type = 'LATE_FEE', origin_invoice_id = ? where id = ?", originInvoiceId, debitNoteId);
        Long personId = jdbc.queryForObject("select min(id) from person", Long.class);
        Long versionId = jdbc.queryForObject("insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, "
                + "proposed_at, direct_publication) values ('LATE_PAYMENT', 'LATE_PAYMENT', cast('{}' as jsonb), 'WITHDRAWN', date '2000-01-01', 'Prueba', ?, now(), false) "
                + "returning id", Long.class, personId);
        seededConfigurationVersionIds.add(versionId);
        jdbc.update("insert into late_fee_charge (debit_note_id, origin_invoice_id, student_current_account_id, period_start, period_end, charged_days, cutoff_kind, "
                        + "fact_date, late_payment_version_id, daily_rate, base_amount, currency, created_at) values (?, ?, ?, ?, ?, 1, 'MONTH_CLOSE', ?, ?, 0.002, 100000.00, ?, now())",
                debitNoteId, originInvoiceId, accountId, factDate, factDate, factDate, versionId, currency.name());
        return debitNoteId;
    }

    protected void seedMarketingDiscount(Long invoiceId, Currency currency, String marketingAmount) {
        Long programId = jdbc.queryForObject("insert into program (name, currency) values ('Carrera de prueba', ?) returning id", Long.class, currency.name());
        seededProgramIds.add(programId);
        Long priceId = jdbc.queryForObject("insert into price (fee_type, currency, description, program_id) values ('ADMINISTRATIVE_FEE', ?, 'Precio de prueba', ?) returning id",
                Long.class, currency.name(), programId);
        seededPriceIds.add(priceId);
        Long personId = jdbc.queryForObject("select min(id) from person", Long.class);
        Long versionId = jdbc.queryForObject("insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, "
                + "proposed_at, decided_by_id, decided_at, direct_publication, initial_version) values (?, 500000.00, ?, date '2000-01-01', 'EFFECTIVE', 'Prueba', ?, now(), ?, now(), true, true) returning id",
                Long.class, priceId, currency.name(), personId, personId);
        jdbc.execute("alter table invoice_line disable trigger trg_invoice_line_immutable_when_issued");
        try {
            jdbc.update("update invoice_line set price_version_id = ?, marketing_discount_amount = ? where invoice_id = ?", versionId, new BigDecimal(marketingAmount), invoiceId);
        } finally {
            jdbc.execute("alter table invoice_line enable trigger trg_invoice_line_immutable_when_issued");
        }
    }

    protected void seedAllocation(Long accountId, Long invoiceId, Currency currency, String amount, LocalDate effectiveDate) {
        NonNegativeMoney money = NonNegativeMoney.of(new BigDecimal(amount), currency);
        long paymentId = nextExternalId();
        inTransaction(() -> {
            Long entryId = service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), paymentId, money, effectiveDate, "Pago de prueba").getId();
            Long allocationId = jdbc.queryForObject("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, effective_date, "
                    + "allocated_on, actor_process, recorded_at) values (?, ?, ?, ?, ?, ?, 'TEST', now()) returning id",
                    Long.class, accountId, invoiceId, money.value().amount(), currency.name(), effectiveDate, effectiveDate);
            jdbc.update("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, credit_entry_id, payment_id, amount, "
                    + "amount_currency, recorded_at) values (?, ?, ?, ?, ?, ?, now())", allocationId, accountId, entryId, paymentId, money.value().amount(), currency.name());
            return null;
        });
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
