package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SubjectEquivalenceGraduationListener {

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger;
    private final ProgramEnrollmentFinder programEnrollmentFinder;

    public SubjectEquivalenceGraduationListener(GraduationRequestAutoRequester graduationRequestAutoRequester,
                                                GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger,
                                                ProgramEnrollmentFinder programEnrollmentFinder) {
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
        this.graduationRequestOutdatedFlagger = graduationRequestOutdatedFlagger;
        this.programEnrollmentFinder = programEnrollmentFinder;
    }

    @EventListener
    public void onEquivalenceRecorded(SubjectEquivalenceRecordedEvent event) {
        if (programEnrollmentFinder.findById(event.programEnrollmentId()).isEmpty()) {
            return;
        }
        graduationRequestAutoRequester.createIfEligible(event.programEnrollmentId());
    }

    @EventListener
    public void onEquivalenceVoided(SubjectEquivalenceVoidedEvent event) {
        graduationRequestOutdatedFlagger.flagOutdatedRequests(event.programEnrollmentId());
    }
}
