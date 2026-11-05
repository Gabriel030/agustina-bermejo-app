package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.ActorSummary;
import com.red_escuela.backend.platform.domain.FeatureChangeEntry;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<FeatureChangeEntry> readPage(FeatureCode code, Pageable pageable) {
        Page<FeatureChangeLog> logs = code == null
                ? changeLogRepository.findAllByOrderByChangedAtDescIdDesc(pageable)
                : changeLogRepository.findAllByCodeOrderByChangedAtDescIdDesc(code, pageable);
        Map<Long, ActorSummary> actors = actorSummaryFinder.findAll(logs.getContent().stream().map(FeatureChangeLog::getActorPersonId).toList());
        return logs.map(log -> new FeatureChangeEntry(log.getId(), log.getCode(), jsonReader.readStateView(log.getBeforeJson()),
                jsonReader.readStateView(log.getAfterJson()), actors.get(log.getActorPersonId()), log.getActorKind().name(), log.getReason(), log.getChangedAt()));
    }
}
