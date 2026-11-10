package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.CycleIncompleteException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleSubmittedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CycleSubmission {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public CycleSubmission(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            CurrentActorFinder currentActorFinder,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void submit(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycle.ensureEditable();

        var subjectSections = subjectSectionRepository.findByAcademicOfferingCycleId(cycleId);
        var courseSections = courseSectionRepository.findByAcademicOfferingCycleId(cycleId);

        if (subjectSections.isEmpty() && courseSections.isEmpty()) {
            throw new CycleIncompleteException("el ciclo no tiene comisiones");
        }

        boolean allHaveLead = subjectSections.stream()
                .allMatch(s -> s.getInstructors().stream().anyMatch(i -> i.getRole() == InstructorRole.LEAD));
        if (!allHaveLead) {
            throw new CycleIncompleteException("hay comisiones sin docente titular asignado");
        }

        boolean allCourseSectionsHaveLead = courseSections.stream()
                .allMatch(s -> s.getInstructors().stream().anyMatch(i -> i.getRole() == InstructorRole.LEAD));
        if (!allCourseSectionsHaveLead) {
            throw new CycleIncompleteException("hay comisiones sin docente titular asignado");
        }

        cycle.submitForApproval();
        cycle.recordSubmission(currentActorFinder.currentPerson(), LocalDateTime.now(clock));
        cycleRepository.save(cycle);
        eventPublisher.publishEvent(AcademicOfferingCycleSubmittedEvent.submittedFor(cycle));
    }
}
