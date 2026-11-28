package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SubjectEquivalenceGraduationListener {

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger;

    public SubjectEquivalenceGraduationListener(GraduationRequestAutoRequester graduationRequestAutoRequester,
                                                GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger) {
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
        this.graduationRequestOutdatedFlagger = graduationRequestOutdatedFlagger;
    }

    @EventListener
    public void onEquivalenceRecorded(SubjectEquivalenceRecordedEvent event) {
        graduationRequestAutoRequester.createIfEligible(event.programEnrollmentId());
    }

    @EventListener
    public void onEquivalenceVoided(SubjectEquivalenceVoidedEvent event) {
        graduationRequestOutdatedFlagger.flagOutdatedRequests(event.programEnrollmentId());
    }
}
