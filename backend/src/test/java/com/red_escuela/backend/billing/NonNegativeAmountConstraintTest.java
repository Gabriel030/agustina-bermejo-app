package com.red_escuela.backend.billing;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-171")
class NonNegativeAmountConstraintTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @ParameterizedTest
    @CsvSource({
            "payment,amount,ck_payment_amount_not_negative",
            "credit_note,amount,ck_credit_note_amount_not_negative",
            "legacy_payment_allocation,amount_applied,ck_payment_allocation_amount_applied_not_negative",
            "credit_application,amount_applied,ck_credit_application_amount_applied_not_negative",
            "price_version,amount,ck_price_version_amount_not_negative"
    })
    void aNegativeAmountViolatesTheCheckConstraint(String table, String column, String constraint) {
        String copy = "tmp_" + table;

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("create temp table " + copy + " (like " + table + " including constraints) on commit drop");
            jdbc.execute("do $$ declare c record; begin for c in select attname from pg_attribute "
                    + "where attrelid = '" + copy + "'::regclass and attnum > 0 and not attisdropped and attnotnull "
                    + "loop execute format('alter table " + copy + " alter column %I drop not null', c.attname); end loop; end $$");
            assertThat(jdbc.update("insert into " + copy + " (" + column + ") values (0.00)")).isEqualTo(1);
            jdbc.update("insert into " + copy + " (" + column + ") values (-0.01)");
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}
