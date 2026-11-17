package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.shared.audit.AuditEntityType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface WaitlistQueue {

    WaitlistKind kind();

    AuditEntityType auditEntityType();

    List<WaitlistQueueSnapshot> findQueues(Long queueId, Long termId);

    List<WaitlistCandidate> findWaitingCandidates(Long queueId);

    Map<Long, Long> findStudentIdByEnrollmentId(Long queueId);

    void lock(Long queueId);

    boolean isOpenForPromotion(Long queueId);

    void promote(Long enrollmentId);

    default Optional<WaitlistEntryOwner> findOwner(Long enrollmentId) {
        throw new UnsupportedOperationException();
    }

    default void markPrioritized(Long enrollmentId, LocalDateTime at) {
        throw new UnsupportedOperationException();
    }

    default void remove(Long enrollmentId) {
        throw new UnsupportedOperationException();
    }

    default boolean hasPendingReceipt(Long enrollmentId) {
        return false;
    }
}
