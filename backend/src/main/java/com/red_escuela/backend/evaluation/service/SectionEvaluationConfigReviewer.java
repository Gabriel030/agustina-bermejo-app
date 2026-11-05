package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigReviewer {

    private final CurrentActorFinder currentActorFinder;

    public SectionEvaluationConfigReviewer(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public void markReviewedByCurrentActor(SectionEvaluationConfig config, SectionEvaluationConfigStatus status) {
        config.setStatus(status);
        config.setReviewedBy(currentActorFinder.currentPerson());
        config.setReviewedAt(ApplicationTime.now());
    }
}
