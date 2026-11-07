package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.ActorSummary;
import com.red_escuela.backend.platform.domain.FeatureDetail;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class FeatureDetailReader {

    private final PlatformFeatureRepository featureRepository;
    private final FeatureChangeLogRepository changeLogRepository;
    private final ActorSummaryFinder actorSummaryFinder;

    public FeatureDetailReader(PlatformFeatureRepository featureRepository, FeatureChangeLogRepository changeLogRepository, ActorSummaryFinder actorSummaryFinder) {
        this.featureRepository = featureRepository;
        this.changeLogRepository = changeLogRepository;
        this.actorSummaryFinder = actorSummaryFinder;
    }

    @Transactional(readOnly = true)
    public List<FeatureDetail> readAll() {
        List<PlatformFeature> features = featureRepository.findAll().stream()
                .sorted(Comparator.comparing(feature -> feature.getCode().ordinal()))
                .toList();
        Map<Long, ActorSummary> actors = actorSummaryFinder.findAll(features.stream().map(PlatformFeature::getUpdatedById).toList());
        return features.stream().map(feature -> toDetail(feature, actors)).toList();
    }

    private FeatureDetail toDetail(PlatformFeature feature, Map<Long, ActorSummary> actors) {
        String lastReason = changeLogRepository.findFirstByCodeOrderByChangedAtDescIdDesc(feature.getCode())
                .map(FeatureChangeLog::getReason)
                .orElse(null);
        return new FeatureDetail(feature.getCode(), feature.isEnabled(), feature.getMode(), feature.getVersion(), feature.getUpdatedAt(),
                actors.get(feature.getUpdatedById()), lastReason);
    }
}
