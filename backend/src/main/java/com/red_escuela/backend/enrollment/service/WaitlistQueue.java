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

    Optional<WaitlistEntryOwner> findOwner(Long enrollmentId);

    void markPrioritized(Long enrollmentId, LocalDateTime at);

    void remove(Long enrollmentId);

    boolean hasReceiptBlockingRemoval(Long enrollmentId);
}
