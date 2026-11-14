package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorView;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.SubjectEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.events.SubjectSectionChangedEvent;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnrollmentChangeEventPublisher {

    private final SectionInstructorFinder sectionInstructorFinder;
    private final ApplicationEventPublisher eventPublisher;

    public EnrollmentChangeEventPublisher(SectionInstructorFinder sectionInstructorFinder, ApplicationEventPublisher eventPublisher) {
        this.sectionInstructorFinder = sectionInstructorFinder;
        this.eventPublisher = eventPublisher;
    }

    public void publishSubjectWithdrawn(SubjectSectionEnrollment enrollment, EnrollmentChange change) {
        Person person = enrollment.getStudent().getPerson();
        eventPublisher.publishEvent(SubjectEnrollmentWithdrawnEvent.of(enrollment.getId(), person.getId(), fullNameOf(person),
                enrollment.getSubjectSection().getSubject().getName(), enrollment.getSubjectSection().getName(),
                instructorPersonIds(enrollment.getSubjectSection().getId()), change.madeByStaff(), change.reason()));
    }

    public void publishSectionChanged(SubjectSectionEnrollment origin, SubjectSectionEnrollment destination, EnrollmentChange change) {
        Person person = origin.getStudent().getPerson();
        eventPublisher.publishEvent(SubjectSectionChangedEvent.of(destination.getId(), person.getId(), fullNameOf(person),
                origin.getSubjectSection().getSubject().getName(), origin.getSubjectSection().getName(),
                destination.getSubjectSection().getName(), instructorPersonIds(origin.getSubjectSection().getId()),
                instructorPersonIds(destination.getSubjectSection().getId()), change.madeByStaff(), change.reason()));
    }

    private List<Long> instructorPersonIds(Long sectionId) {
        return sectionInstructorFinder.findProgramSectionInstructors(sectionId).stream().map(SectionInstructorView::personId).toList();
    }

    private static String fullNameOf(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
