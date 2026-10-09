package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository extends Repository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findById(String idempotencyKey);

    @Modifying
    @Query(value = """
            insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at)
            values (:key, :operationType, :requestHash, :createdAt)
            on conflict (idempotency_key) do nothing
            """, nativeQuery = true)
    int insertKeyIfAbsent(@Param("key") String key, @Param("operationType") String operationType,
                          @Param("requestHash") String requestHash, @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :limit")
    int deleteRecordsCreatedBefore(@Param("limit") LocalDateTime limit);
}
