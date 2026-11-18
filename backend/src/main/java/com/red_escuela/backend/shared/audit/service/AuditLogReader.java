package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.audit.AuditLogSpecifications;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
@Transactional(readOnly = true)
public class AuditLogReader {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditLogRepository auditLogRepository;

    public AuditLogReader(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public PageResponse<AuditLogView> search(AuditLogFilter filter, int page, int size,
                                             Function<Collection<Long>, Map<Long, String>> actorNameResolver) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new RequestFieldOutOfRangeException("El campo from no puede ser posterior a to");
        }
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<AuditLogEntry> result = auditLogRepository.findAll(AuditLogSpecifications.matching(filter),
                PageRequest.of(boundedPage, boundedSize, NEWEST_FIRST));
        List<Long> actorIds = result.getContent().stream().map(AuditLogEntry::getActorPersonId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = actorIds.isEmpty() ? Map.of() : actorNameResolver.apply(actorIds);
        List<AuditLogView> items = result.getContent().stream()
                .map(entry -> AuditLogView.from(entry, entry.getActorPersonId() == null ? null : names.get(entry.getActorPersonId()))).toList();
        return new PageResponse<>(items, boundedPage, boundedSize, result.getTotalElements());
    }
}
