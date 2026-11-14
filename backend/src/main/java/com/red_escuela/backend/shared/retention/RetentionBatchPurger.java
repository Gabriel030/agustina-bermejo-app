package com.red_escuela.backend.shared.retention;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RetentionBatchPurger {

    public static final String METER_NAME = "retention.rows.deleted";

    private static final Logger log = LoggerFactory.getLogger(RetentionBatchPurger.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate batchTransaction;
    private final MeterRegistry meterRegistry;
    private final RetentionSettings settings;

    public RetentionBatchPurger(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager, MeterRegistry meterRegistry,
                                RetentionSettings settings) {
        this.jdbc = jdbc;
        this.batchTransaction = new TransactionTemplate(transactionManager);
        this.batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meterRegistry = meterRegistry;
        this.settings = settings;
    }

    public int purge(String label, String table, String eligibleCondition, MapSqlParameterSource parameters) {
        return purge(label, table, "id", eligibleCondition, parameters);
    }

    public int purge(String label, String table, String keyColumns, String eligibleCondition, MapSqlParameterSource parameters) {
        String sql = "delete from " + table + " where (" + keyColumns + ") in (select " + keyColumns + " from " + table
                + " where " + eligibleCondition + " order by " + keyColumns + " limit :retentionBatchSize)";
        MapSqlParameterSource batchParameters = new MapSqlParameterSource(parameters.getValues())
                .addValue("retentionBatchSize", settings.batchSize());
        int total = 0;
        for (int batch = 0; batch < settings.maxBatchesPerRun(); batch++) {
            Integer deleted = batchTransaction.execute(status -> jdbc.update(sql, batchParameters));
            int count = deleted == null ? 0 : deleted;
            total += count;
            if (count < settings.batchSize()) {
                break;
            }
        }
        meterRegistry.counter(METER_NAME, "table", label).increment(total);
        log.info("Retencion de datos operativos tabla={} filasBorradas={}", label, total);
        return total;
    }
}
