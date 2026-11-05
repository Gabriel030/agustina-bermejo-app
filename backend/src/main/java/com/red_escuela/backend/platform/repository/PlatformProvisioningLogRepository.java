package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.PlatformProvisioningLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformProvisioningLogRepository extends JpaRepository<PlatformProvisioningLog, Long> {

    Optional<PlatformProvisioningLog> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:lockKey)) lock_acquired", nativeQuery = true)
    long lockProvisioningUntilTransactionEnds(@Param("lockKey") long lockKey);
}
