package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-214")
@Tag("D-240")
class PriceSchemaConstraintIT extends AbstractInvoiceIssuingIT {

    private void expectRejection(String expectedMessagePart, Runnable statements) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                statements.run();
            } finally {
                status.setRollbackOnly();
            }
        })).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(expectedMessagePart);
    }

    private long monthlyPriceVersionOf(IssuingScenarioFactory.Offering offering) {
        return jdbc.queryForObject("select v.id from price_version v join price p on p.id = v.price_id where p.program_id = ? and p.fee_type = 'MONTHLY_INSTALLMENT'",
                Long.class, offering.programId());
    }

    private long monthlyPriceOf(IssuingScenarioFactory.Offering offering) {
        return jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", Long.class, offering.programId());
    }

    private void insertVersion(long priceId, String status, LocalDate from, String amount, String discountType, String discountValue, Long directorId, String cancelReason, String decisionReason) {
        jdbc.update("""
                insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, proposed_at,
                    decided_by_id, decided_at, marketing_discount_type, marketing_discount_percentage, marketing_discount_amount, marketing_discount_amount_currency,
                    cancellation_reason, decision_reason)
                values (?, cast(? as numeric), 'ARS', ?, ?, 'test', ?, now(), ?, now(), ?, case when ? = 'PERCENTAGE' then cast(? as numeric) / 100 end,
                    case when ? = 'FIXED' then cast(? as numeric) end, case when ? = 'FIXED' then 'ARS' end, ?, ?)
                """, priceId, amount, from, status, directorId, "PROPOSED".equals(status) ? null : directorId, discountType, discountType, discountValue,
                discountType, discountValue, discountType, cancelReason, decisionReason);
    }

    @Test
    void everyValueColumnOfAPriceVersionRefusesToChangeButItsStatusDoesNot() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var other = factory.newProgram(Currency.ARS, 100000, 50000);
        long version = monthlyPriceVersionOf(offering);
        long otherPrice = monthlyPriceOf(other);

        for (String assignment : new String[]{"amount = 1", "amount_currency = 'USD'", "effective_from = date '2030-01-01'", "marketing_discount_type = 'FIXED', marketing_discount_amount = 1, marketing_discount_amount_currency = 'ARS'",
                "marketing_discount_type = 'PERCENTAGE', marketing_discount_percentage = 0.1"}) {
            expectRejection("price_version values are immutable", () -> jdbc.update("update price_version set " + assignment + " where id = ?", version));
        }
        expectRejection("price_version values are immutable", () -> jdbc.update("update price_version set price_id = ? where id = ?", otherPrice, version));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(jdbc.update("update price_version set status = 'SUPERSEDED', decision_reason = 'replaced' where id = ?", version)).isEqualTo(1);
            status.setRollbackOnly();
        });
    }

    @Test
    void theMarketingDiscountIsImmutableOnceSetToo() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        long priceId = monthlyPriceOf(offering);
        for (String assignment : new String[]{"marketing_discount_percentage = 0.3", "marketing_discount_type = null, marketing_discount_percentage = null",
                "marketing_discount_type = 'FIXED', marketing_discount_percentage = null, marketing_discount_amount = 20, marketing_discount_amount_currency = 'ARS'"}) {
            expectRejection("price_version values are immutable", () -> {
                insertVersion(priceId, "SCHEDULED", LocalDate.of(2090, 1, 1), "100000", "PERCENTAGE", "20", offering.directorId(), null, null);
                jdbc.update("update price_version set " + assignment + " where price_id = ? and effective_from = date '2090-01-01'", priceId);
            });
        }
    }

    @Test
    void thePriceAndVersionConstraintsRejectEveryViolatingRow() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        long priceId = monthlyPriceOf(offering);
        Long director = offering.directorId();
        LocalDate future = LocalDate.of(2091, 1, 1);

        expectRejection("uk_price_program_fee", () -> jdbc.update("insert into price (program_id, fee_type, currency, description) values (?, 'MONTHLY_INSTALLMENT', 'ARS', 'dup')", offering.programId()));
        expectRejection("ck_price_single_offering", () -> jdbc.update("insert into price (fee_type, currency, description) values ('MONTHLY_INSTALLMENT', 'ARS', 'none')"));
        expectRejection("ck_price_single_offering", () -> jdbc.update("insert into price (program_id, course_id, fee_type, currency, description) values (?, 1, 'ENROLLMENT_FEE', 'ARS', 'both')", offering.programId()));
        expectRejection("uk_price_version_effective_date", () -> {
            insertVersion(priceId, "SCHEDULED", future, "1", null, null, director, null, null);
            insertVersion(priceId, "SCHEDULED", future, "2", null, null, director, null, null);
        });
        expectRejection("uk_price_version_open_proposal", () -> {
            insertVersion(priceId, "PROPOSED", future, "1", null, null, director, null, null);
            insertVersion(priceId, "PROPOSED", future.plusDays(1), "2", null, null, director, null, null);
        });
        expectRejection("ck_price_version_amount_not_negative", () -> insertVersion(priceId, "SCHEDULED", future, "-1", null, null, director, null, null));
        expectRejection("ck_price_version_marketing_discount", () -> insertVersion(priceId, "SCHEDULED", future, "100", "PERCENTAGE", "120", director, null, null));
        expectRejection("ck_price_version_marketing_discount", () -> insertVersion(priceId, "SCHEDULED", future, "100", "FIXED", "150", director, null, null));
        expectRejection("ck_price_version_marketing_discount", () -> insertVersion(priceId, "SCHEDULED", future, "100", "FIXED", "-1", director, null, null));
        expectRejection("ck_price_version_marketing_discount", () -> insertVersion(priceId, "SCHEDULED", future, "100", "PERCENTAGE", null, director, null, null));
        expectRejection("ck_price_version_status", () -> insertVersion(priceId, "BOGUS", future, "1", null, null, director, null, null));
        expectRejection("ck_price_version_cancel_reason", () -> insertVersion(priceId, "CANCELLED", future, "1", null, null, director, null, null));
        expectRejection("ck_price_version_reject_reason", () -> insertVersion(priceId, "REJECTED", future, "1", null, null, director, null, null));
        transactionTemplate.executeWithoutResult(status -> {
            insertVersion(priceId, "SCHEDULED", future, "100", "FIXED", "100", director, null, null);
            insertVersion(priceId, "SCHEDULED", future.plusDays(1), "100", "PERCENTAGE", "100", director, null, null);
            status.setRollbackOnly();
        });
    }

    @Test
    void theStatusHistoryOfAPriceVersionCannotBeRewritten() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        long priceId = monthlyPriceOf(offering);
        for (String statement : new String[]{"update price_version_status_change set reason = 'x'", "delete from price_version_status_change"}) {
            expectRejection("append-only", () -> {
                insertVersion(priceId, "SCHEDULED", LocalDate.of(2092, 1, 1), "1", null, null, offering.directorId(), null, null);
                long version = jdbc.queryForObject("select id from price_version where price_id = ? and effective_from = date '2092-01-01'", Long.class, priceId);
                jdbc.update("insert into price_version_status_change (price_version_id, to_status, event, actor_process, changed_at) values (?, 'SCHEDULED', 'PUBLISH_FOR_FUTURE', 'test', now())", version);
                jdbc.update(statement + " where price_version_id = ?", version);
            });
        }
    }

    @Test
    void anInvoiceLineRefusesANegativeMarketingDiscountAndOneWithoutItsPriceVersion() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var learner = factory.newLearner(offering, null);
        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        long lineId = invoice.getLines().get(0).getId();
        long version = monthlyPriceVersionOf(offering);
        String copy = """
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount, discount_amount_currency,
                    credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency, price_version_id, marketing_discount_amount)
                select invoice_id, currency, 9, 'ADMINISTRATIVE_FEE', 'copy', period, 'copy-' || nextval('invoice_line_id_seq'), 100, amount_currency, 0, discount_amount_currency, 0,
                    credited_amount_currency, 0, free_credited_amount_currency, ?, ? from invoice_line where id = ?
                """;

        expectRejection("marketing_discount_amount", () -> jdbc.update(copy, version, -1, lineId));
        expectRejection("ck_invoice_line_marketing_discount_has_price_version", () -> jdbc.update(copy, null, 10, lineId));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(jdbc.update(copy, version, 10, lineId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select list_amount from invoice_line where line_number = 9 and invoice_id = ?", java.math.BigDecimal.class, invoice.getId()))
                    .isEqualByComparingTo("110.00");
            status.setRollbackOnly();
        });
    }

    @Test
    void anIssuedInvoiceLineRefusesToChangeItsMarketingDiscountAndItsPriceVersion() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var learner = factory.newLearner(offering, null);
        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        long lineId = invoice.getLines().get(0).getId();
        long otherVersion = monthlyPriceVersionOf(factory.newProgram(Currency.ARS, 100000, 50000));

        expectRejection("issued invoice line is immutable", () -> jdbc.update("update invoice_line set marketing_discount_amount = 5 where id = ?", lineId));
        expectRejection("issued invoice line is immutable", () -> jdbc.update("update invoice_line set price_version_id = ? where id = ?", otherVersion, lineId));
        expectRejection("issued invoice line is immutable", () -> jdbc.update("update invoice_line set price_version_id = null where id = ?", lineId));
    }
}
