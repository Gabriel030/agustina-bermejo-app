package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.exception.WaitlistQueueNotFoundException;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogArchiveEntry;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.audit.service.AuditLogArchiveReader;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class WaitlistHistoryReader {

    private static final Comparator<HistoryRow> NEWEST_FIRST = Comparator.comparing(HistoryRow::occurredAt)
            .thenComparing(HistoryRow::id).reversed();

    private final WaitlistQueues waitlistQueues;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogArchiveReader auditLogArchiveReader;
    private final PersonNameFinder personNameFinder;

    public WaitlistHistoryReader(WaitlistQueues waitlistQueues, AuditLogRepository auditLogRepository,
                                 AuditLogArchiveReader auditLogArchiveReader, PersonNameFinder personNameFinder) {
        this.waitlistQueues = waitlistQueues;
        this.auditLogRepository = auditLogRepository;
        this.auditLogArchiveReader = auditLogArchiveReader;
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
        AuditEntityType entityType = queue.auditEntityType();
        Set<Long> enrollmentIds = studentIdByEnrollmentId.keySet();
        int window = (int) Math.min((long) (boundedPage + 1) * boundedSize, Integer.MAX_VALUE);
        Page<AuditLogEntry> live = auditLogRepository.findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(
                entityType, enrollmentIds, PageRequest.of(0, window));
        Page<AuditLogArchiveEntry> archived = auditLogArchiveReader.findNewestFirst(entityType, enrollmentIds, window);
        List<HistoryRow> rows = Stream.concat(live.stream().map(HistoryRow::fromLive),
                        archived.stream().map(HistoryRow::fromArchive))
                .sorted(NEWEST_FIRST)
                .skip((long) boundedPage * boundedSize)
                .limit(boundedSize)
                .toList();
        Set<Long> actorIds = new HashSet<>();
        rows.forEach(row -> {
            if (row.actorPersonId() != null) {
                actorIds.add(row.actorPersonId());
            }
        });
        Map<Long, String> actorNames = personNameFinder.findFullNamesByPersonId(actorIds);
        Map<Long, String> studentNames = personNameFinder.findFullNamesByStudentId(new HashSet<>(studentIdByEnrollmentId.values()));
        List<WaitlistHistoryEntryResponse> items = rows.stream()
                .map(row -> new WaitlistHistoryEntryResponse(row.occurredAt(), row.action().name(),
                        row.actorPersonId() == null ? null : actorNames.get(row.actorPersonId()),
                        studentNames.get(studentIdByEnrollmentId.get(row.entityId())), row.reason()))
                .toList();
        return new PageResponse<>(items, boundedPage, boundedSize, live.getTotalElements() + archived.getTotalElements());
    }

    private record HistoryRow(Long id, LocalDateTime occurredAt, Long actorPersonId, AuditAction action, Long entityId,
                              String reason) {

        static HistoryRow fromLive(AuditLogEntry entry) {
            return new HistoryRow(entry.getId(), entry.getOccurredAt(), entry.getActorPersonId(), entry.getAction(),
                    entry.getEntityId(), entry.getReason());
        }

        static HistoryRow fromArchive(AuditLogArchiveEntry entry) {
            return new HistoryRow(entry.getId(), entry.getOccurredAt(), entry.getActorPersonId(), entry.getAction(),
                    entry.getEntityId(), entry.getReason());
        }
    }
}
