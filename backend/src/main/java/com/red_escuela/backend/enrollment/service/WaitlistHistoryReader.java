package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.exception.WaitlistQueueNotFoundException;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class WaitlistHistoryReader {

    private final WaitlistQueues waitlistQueues;
    private final AuditLogRepository auditLogRepository;
    private final PersonNameFinder personNameFinder;

    public WaitlistHistoryReader(WaitlistQueues waitlistQueues, AuditLogRepository auditLogRepository,
                                     PersonNameFinder personNameFinder) {
        this.waitlistQueues = waitlistQueues;
        this.auditLogRepository = auditLogRepository;
        this.personNameFinder = personNameFinder;
    }

    public PageResponse<WaitlistHistoryEntryResponse> read(WaitlistKind kind, Long queueId, int page, int size) {
        WaitlistQueue queue = waitlistQueues.of(kind);
        if (queue.findQueues(queueId, null).isEmpty()) {
            throw new WaitlistQueueNotFoundException(kind.name(), queueId);
        }
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Map<Long, Long> studentIdByEnrollmentId = queue.findStudentIdByEnrollmentId(queueId);
        if (studentIdByEnrollmentId.isEmpty()) {
            return new PageResponse<>(List.of(), boundedPage, boundedSize, 0);
        }
        Page<AuditLogEntry> entries = auditLogRepository.findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(
                queue.auditEntityType(), studentIdByEnrollmentId.keySet(), PageRequest.of(boundedPage, boundedSize));
        Set<Long> actorIds = new HashSet<>();
        entries.forEach(entry -> {
            if (entry.getActorPersonId() != null) {
                actorIds.add(entry.getActorPersonId());
            }
        });
        Map<Long, String> actorNames = personNameFinder.findFullNamesByPersonId(actorIds);
        Map<Long, String> studentNames = personNameFinder.findFullNamesByStudentId(new HashSet<>(studentIdByEnrollmentId.values()));
        List<WaitlistHistoryEntryResponse> items = entries.stream()
                .map(entry -> new WaitlistHistoryEntryResponse(entry.getOccurredAt(), entry.getAction().name(),
                        entry.getActorPersonId() == null ? null : actorNames.get(entry.getActorPersonId()),
                        studentNames.get(studentIdByEnrollmentId.get(entry.getEntityId())), entry.getReason()))
                .toList();
        return new PageResponse<>(items, boundedPage, boundedSize, entries.getTotalElements());
    }
}
