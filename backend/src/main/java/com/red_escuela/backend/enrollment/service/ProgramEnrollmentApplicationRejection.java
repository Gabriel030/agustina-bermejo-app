package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProgramEnrollmentApplicationRejection {

    private final ProgramEnrollmentApplicationFinder applicationFinder;

    public ProgramEnrollmentApplicationRejection(ProgramEnrollmentApplicationFinder applicationFinder) {
        this.applicationFinder = applicationFinder;
    }

    @Transactional
    public ProgramEnrollmentApplication reject(Long applicationId, Person decidedBy, String reason) {
        return reject(applicationId, decidedBy, reason, null);
    }

    @Transactional
    public ProgramEnrollmentApplication reject(Long applicationId, Person decidedBy, String reason, String idempotencyKey) {
        String key = ApplicationDecisionIdempotencyKeys.normalize(idempotencyKey);
        ProgramEnrollmentApplication application = applicationFinder.findLockedOrThrow(applicationId);
        if (ApplicationDecisionIdempotencyKeys.isReplayOfRejection(application, key)) {
            return application;
        }
        application.reject(decidedBy, reason);
        application.recordDecisionKey(key);
        return application;
    }
}
