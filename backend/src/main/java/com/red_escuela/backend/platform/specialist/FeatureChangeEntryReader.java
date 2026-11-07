package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.ActorSummary;
import com.red_escuela.backend.platform.domain.FeatureChangeEntry;
import com.red_escuela.backend.platform.domain.FeatureChangeFilter;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class FeatureChangeEntryReader {

    private final FeatureChangeLogRepository changeLogRepository;
    private final FeatureChangeJsonReader jsonReader;
    private final ActorSummaryFinder actorSummaryFinder;

    public FeatureChangeEntryReader(FeatureChangeLogRepository changeLogRepository, FeatureChangeJsonReader jsonReader, ActorSummaryFinder actorSummaryFinder) {
        this.changeLogRepository = changeLogRepository;
        this.jsonReader = jsonReader;
        this.actorSummaryFinder = actorSummaryFinder;
    }

    @Transactional(readOnly = true)
    public Page<FeatureChangeEntry> readPage(FeatureChangeFilter filter, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id")));
        Page<FeatureChangeLog> logs = changeLogRepository.findAll(matching(filter), newestFirst);
        Map<Long, ActorSummary> actors = actorSummaryFinder.findAll(logs.getContent().stream().map(FeatureChangeLog::getActorPersonId).toList());
        return logs.map(log -> new FeatureChangeEntry(log.getId(), log.getCode(), jsonReader.readStateView(log.getBeforeJson()),
                jsonReader.readStateView(log.getAfterJson()), actors.get(log.getActorPersonId()), log.getActorKind().name(), log.getReason(), log.getChangedAt()));
    }

    private Specification<FeatureChangeLog> matching(FeatureChangeFilter filter) {
        Specification<FeatureChangeLog> spec = Specification.unrestricted();
        if (filter.code() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("code"), filter.code()));
        }
        if (filter.actorPersonId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorPersonId"), filter.actorPersonId()));
        }
        if (filter.actorKind() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorKind"), filter.actorKind()));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("changedAt"), filter.from().atStartOfDay()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("changedAt"), filter.to().plusDays(1).atStartOfDay()));
        }
        return spec;
    }
}
