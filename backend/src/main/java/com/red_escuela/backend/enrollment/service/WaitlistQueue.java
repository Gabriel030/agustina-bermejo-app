package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.shared.audit.AuditEntityType;

import java.util.List;
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
}
