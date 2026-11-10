package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleNotPendingApprovalException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleApprovedEvent;
import com.red_escuela.backend.academicstructure.exception.CycleDecisionIdempotencyKeyConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CycleApproval {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionConflictChecker conflictChecker;
    private final AcademicOfferingCycleReviewer cycleReviewer;
    private final ApplicationEventPublisher eventPublisher;

    public CycleApproval(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionConflictChecker conflictChecker,
            AcademicOfferingCycleReviewer cycleReviewer,
            ApplicationEventPublisher eventPublisher
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.slotRepository = slotRepository;
        this.conflictChecker = conflictChecker;
        this.cycleReviewer = cycleReviewer;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void approve(Long cycleId, String idempotencyKey) {
        var key = CycleDecisionIdempotencyKeys.normalize(idempotencyKey);
        var cycle = cycleRepository.findByIdForUpdate(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        if (cycle.wasDecidedWithKey(key)) {
            if (cycle.getStatus() == AcademicOfferingCycleStatus.APPROVED) {
                return;
            }
            throw new CycleDecisionIdempotencyKeyConflictException(cycleId);
        }
        if (cycle.getStatus() != AcademicOfferingCycleStatus.PENDING_APPROVAL) {
            throw new CycleNotPendingApprovalException(cycleId);
        }

        var subjectSections = subjectSectionRepository.findByAcademicOfferingCycleId(cycleId);
        for (var section : subjectSections) {
            for (var slot : slotRepository.findBySubjectSectionId(section.getId())) {
                conflictChecker.checkClassroomAvailability(cycle.getTerm(),
                        slot.getClassroom().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
                conflictChecker.checkSubjectSlotInstructorsAvailability(cycle, section, slot);
            }
        }

        var courseSections = courseSectionRepository.findByAcademicOfferingCycleId(cycleId);
        for (var section : courseSections) {
            for (var slot : slotRepository.findByCourseSectionId(section.getId())) {
                conflictChecker.checkClassroomAvailability(cycle.getTerm(),
                        slot.getClassroom().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
                conflictChecker.checkCourseSlotInstructorsAvailability(cycle, section, slot);
            }
        }

        subjectSections.forEach(s -> { s.setStatus(StatusType.ACTIVE); subjectSectionRepository.save(s); });
        courseSections.forEach(s -> { s.setStatus(StatusType.ACTIVE); courseSectionRepository.save(s); });

        cycleReviewer.approve(cycle, key);
        cycleRepository.save(cycle);
        eventPublisher.publishEvent(AcademicOfferingCycleApprovedEvent.approvedFor(cycle));
    }
}
