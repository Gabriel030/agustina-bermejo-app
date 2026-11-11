package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSectionView;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SectionWaitlistPromoter {

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final SectionFinder sectionFinder;
    private final SubjectRetakeGuard subjectRetakeGuard;
    private final ProgramPrerequisiteEvaluator prerequisiteEvaluator;

    public SectionWaitlistPromoter(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                   SectionFinder sectionFinder, SubjectRetakeGuard subjectRetakeGuard,
                                   ProgramPrerequisiteEvaluator prerequisiteEvaluator) {
        this.prerequisiteEvaluator = prerequisiteEvaluator;
        this.subjectRetakeGuard = subjectRetakeGuard;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.sectionFinder = sectionFinder;
    }

    @Transactional
    public void promoteNextFromWaitlist(Long sectionId) {
        sectionFinder.lockProgramSection(sectionId);
        ProgramSectionView section = sectionFinder.findProgramSection(sectionId);
        int capacity = section.capacity() == null ? Integer.MAX_VALUE : section.capacity();
        long occupied = subjectSectionEnrollmentRepository.countBySubjectSectionIdAndStatusIn(
                sectionId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
        if (occupied >= capacity) {
            return;
        }
        subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED)
                .stream()
                .filter(candidate -> !subjectRetakeGuard.isAlreadyPassed(
                        candidate.getProgramEnrollment().getId(), candidate.subjectId(), candidate.getId()))
                .filter(candidate -> prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(
                        candidate.getProgramEnrollment().getId(), candidate.subjectId()))
                .findFirst()
                .ifPresent(next -> {
                    next.setStatus(EnrollmentStatus.ENROLLED);
                    subjectSectionEnrollmentRepository.save(next);
                });
    }
}
