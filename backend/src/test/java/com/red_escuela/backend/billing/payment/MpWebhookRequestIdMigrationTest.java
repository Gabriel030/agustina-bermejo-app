package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class MpWebhookRequestIdMigrationTest {

    private static final String INSERT_EVENT = """
            insert into mp_webhook_received_event (received_at, x_signature, x_request_id, mp_payment_id, billing_mode_at_receipt, processing_status)
            values (now(), 'ts=1,v1=abc', ?, ?, 'OFF', 'RECEIVED')""";

    @Test
    void historicalDuplicatesKeepTheLowestIdAndTheUniqueIndexIsCreated() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("189")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update(INSERT_EVENT, "request-dup", "1");
            jdbc.update(INSERT_EVENT, "request-dup", "2");
            jdbc.update(INSERT_EVENT, "request-dup", "3");
            jdbc.update(INSERT_EVENT, "request-single", "4");
            jdbc.update(INSERT_EVENT, "", "5");
            jdbc.update(INSERT_EVENT, "", "6");
            Long lowestDuplicateId = jdbc.queryForObject("select min(id) from mp_webhook_received_event where x_request_id = 'request-dup'", Long.class);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            List<Long> keptIds = jdbc.queryForList("select id from mp_webhook_received_event where x_request_id = 'request-dup'", Long.class);
            assertThat(keptIds).containsExactly(lowestDuplicateId);
            assertThat(jdbc.queryForObject("select count(*) from mp_webhook_received_event where x_request_id = 'request-single'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from mp_webhook_received_event where x_request_id = ''", Integer.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update(INSERT_EVENT, "request-single", "7"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(INSERT_EVENT, "", "8");
        }
    }

    @Test
    void migrationOnAnEmptyTableCreatesTheIndex() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("189")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'uq_mp_webhook_received_event_request_id'", Integer.class)).isEqualTo(1);
        }
    }
}
