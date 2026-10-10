package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.domain.IdempotencyRecord;
import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyConflictException;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseContext;
import com.red_escuela.backend.billing.payment.repository.IdempotencyRecordRepository;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.time.ApplicationTime;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyRecordService {

    public static final int PROCESSED_STATUS = 201;
    public static final int AWAITING_AUTHORIZATION_STATUS = 202;
    private static final int RETENTION_DAYS = 7;

    private final IdempotencyRecordRepository repository;
    private final PolicySet<IdempotencyKeyReuseContext> reusePolicySet;
    private final MeterRegistry meterRegistry;

    public IdempotencyRecordService(IdempotencyRecordRepository repository, PolicySet<IdempotencyKeyReuseContext> idempotencyKeyReusePolicySet,
                                    MeterRegistry meterRegistry) {
        this.repository = repository;
        this.reusePolicySet = idempotencyKeyReusePolicySet;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StoredIdempotentResponse claimKeyOrFindStoredResponse(String key, BillingOperationType type, String requestHash, PriorAuthorization priorAuthorization) {
        int inserted = repository.insertKeyIfAbsent(key, type.name(), requestHash, ApplicationTime.now());
        if (inserted == 1) {
            return StoredIdempotentResponse.KeyClaimed.INSTANCE;
        }
        IdempotencyRecord existing = repository.findByIdLockingForUpdate(key).orElseThrow();
        reusePolicySet.evaluateRulesUntilFirstViolation(new IdempotencyKeyReuseContext(existing.getOperationType(), existing.getRequestHash(), type.name(), requestHash))
                .throwIfDenied(IdempotencyKeyConflictException::new);
        boolean resumedAfterAuthorization = priorAuthorization == PriorAuthorization.APPROVED
                && Optional.ofNullable(existing.getResponseStatus()).filter(status -> status == AWAITING_AUTHORIZATION_STATUS).isPresent();
        if (!existing.hasStoredResponse() || resumedAfterAuthorization) {
            return StoredIdempotentResponse.KeyClaimed.INSTANCE;
        }
        meterRegistry.counter("billing.idempotency.replays").increment();
        return new StoredIdempotentResponse.Replayable(existing.getResponseStatus(), existing.getResponseBody());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void storeResponse(String key, int status, String body) {
        repository.findById(key).orElseThrow().storeResponse(status, body);
    }

    @Transactional
    public int deleteRecordsOlderThanRetention() {
        LocalDateTime limit = ApplicationTime.now().minusDays(RETENTION_DAYS);
        return repository.deleteRecordsCreatedBefore(limit);
    }
}
