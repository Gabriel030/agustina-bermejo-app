package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.ActorSummary;
import com.red_escuela.backend.platform.domain.InstituteSettingChangeEntry;
import com.red_escuela.backend.platform.entity.InstituteSettingChangeLog;
import com.red_escuela.backend.platform.repository.InstituteSettingChangeLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class InstituteSettingChangeEntryReader {

    private final InstituteSettingChangeLogRepository changeLogRepository;
    private final ActorSummaryFinder actorSummaryFinder;

    public InstituteSettingChangeEntryReader(InstituteSettingChangeLogRepository changeLogRepository, ActorSummaryFinder actorSummaryFinder) {
        this.changeLogRepository = changeLogRepository;
        this.actorSummaryFinder = actorSummaryFinder;
    }

    @Transactional(readOnly = true)
    public Page<InstituteSettingChangeEntry> readPage(Pageable pageable) {
        Page<InstituteSettingChangeLog> logs = changeLogRepository.findAllByOrderByChangedAtDescIdDesc(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        Map<Long, ActorSummary> actors = actorSummaryFinder.findAll(logs.getContent().stream().map(InstituteSettingChangeLog::getActorPersonId).toList());
        return logs.map(log -> new InstituteSettingChangeEntry(log.getId(), log.getCode(), log.getBeforeEnabled(), log.isAfterEnabled(),
                actors.get(log.getActorPersonId()), log.getReason(), log.getChangedAt()));
    }
}
