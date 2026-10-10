package com.red_escuela.backend.billing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditNoteCutoverMigrationTest {

    @Test
    @Tag("D-144")
    @Tag("D-165")
    void ledgerEntriesPointingToLegacyCreditNotesAreRemovedAndTheLedgerIsRebuiltBeforeTheForeignKeyExists() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("102").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into institution (name) values ('Instituto sembrado')");
            Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
            jdbc.update("insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
            Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
            jdbc.update("insert into credit_note (student_id, amount, amount_currency, reason_type, reason, status, created_by_id, created_at, updated_at) "
                    + "values (?, 1500.00, 'ARS', 'ADMINISTRATIVE_CORRECTION', 'Ajuste', 'PARTIALLY_CONSUMED', ?, now(), now())", studentId, personId);
            Long legacyNoteId = jdbc.queryForObject("select max(id) from credit_note", Long.class);
            jdbc.update("insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side, unallocated_credit_amount, "
                    + "unallocated_credit_amount_currency, last_entry_sequence, opened_at) values (?, 'ARS', 7500.00, 'ARS', 'IN_FAVOR', 7500.00, 'ARS', 3, now())", studentId);
            Long accountId = jdbc.queryForObject("select max(id) from student_current_account", Long.class);
            insertEntry(jdbc, accountId, 1, "PAYMENT_RECEIVED", "5000.00", "5000.00", null, 501L);
            insertEntry(jdbc, accountId, 2, "CREDIT_NOTE_ISSUED", "1500.00", "6500.00", legacyNoteId, null);
            insertEntry(jdbc, accountId, 3, "PAYMENT_RECEIVED", "1000.00", "7500.00", null, 502L);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("103").load().migrate();

            List<Map<String, Object>> entries = jdbc.queryForList("select sequence_number, entry_type, running_balance_amount, running_balance_side from current_account_entry "
                    + "where student_current_account_id = ? order by sequence_number", accountId);
            assertThat(entries).extracting(row -> row.get("sequence_number") + ":" + row.get("entry_type")).containsExactly("1:PAYMENT_RECEIVED", "2:PAYMENT_RECEIVED");
            assertThat(entries.get(1).get("running_balance_amount")).hasToString("6000.00");
            assertThat(entries.get(1).get("running_balance_side")).isEqualTo("IN_FAVOR");
            Map<String, Object> account = jdbc.queryForMap("select balance_amount, balance_side, unallocated_credit_amount, last_entry_sequence from student_current_account where id = ?", accountId);
            assertThat(account.get("balance_amount")).hasToString("6000.00");
            assertThat(account.get("balance_side")).isEqualTo("IN_FAVOR");
            assertThat(account.get("unallocated_credit_amount")).hasToString("6000.00");
            assertThat(account.get("last_entry_sequence")).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select count(*) from current_account_entry where credit_note_id is not null", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname = 'fk_current_account_entry_credit_note'", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname = 'trg_current_account_entry_append_only' and tgenabled = 'O'", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from credit_note", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_name = 'authorization_request' and column_name = 'reason'", Integer.class))
                    .isEqualTo(1200);
            assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_name = 'authorization_request_status_change' and column_name = 'reason'", Integer.class))
                    .isEqualTo(1200);
        }
    }

    @Test
    @Tag("D-144")
    void theMigrationAbortsWhenThereAreCreditBalancePaymentsOrLegacyCreditApplications() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("102").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at) "
                    + "values (100.00, 'ARS', 'CREDIT_BALANCE', ?, now(), 'CONFIRMED', now(), now())", personId);

            assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("103").load().migrate())
                    .hasMessageContaining("V103 no migra pagos con saldo a favor");
            assertThat(jdbc.queryForObject("select count(*) from payment", Long.class)).isEqualTo(1L);
        }
    }

    private void insertEntry(JdbcTemplate jdbc, Long accountId, int sequence, String type, String amount, String running, Long creditNoteId, Long paymentId) {
        jdbc.update("insert into current_account_entry (student_current_account_id, currency, sequence_number, direction, entry_type, amount, amount_currency, "
                + "running_balance_amount, running_balance_amount_currency, running_balance_side, effective_date, recorded_at, concept, payment_id, credit_note_id) "
                + "values (?, 'ARS', ?, 'CREDIT', ?, ?::numeric, 'ARS', ?::numeric, 'ARS', 'IN_FAVOR', date '2027-03-01', now(), 'c', ?, ?)",
                accountId, sequence, type, amount, running, paymentId, creditNoteId);
    }
}
