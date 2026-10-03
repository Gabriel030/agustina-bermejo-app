package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleNotPendingApprovalException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class ApproveCycleService implements ApproveCycleUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionConflictChecker conflictChecker;
    private final CurrentPersonService currentPersonService;

    public ApproveCycleService(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionConflictChecker conflictChecker,
            CurrentPersonService currentPersonService
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.slotRepository = slotRepository;
        this.conflictChecker = conflictChecker;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public void approve(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        if (cycle.getStatus() != AcademicOfferingCycleStatus.PENDING_APPROVAL) {
            throw new CycleNotPendingApprovalException(cycleId);
        }

        var subjectSections = subjectSectionRepository.findByAcademicOfferingCycleId(cycleId);
        for (var section : subjectSections) {
            for (var slot : slotRepository.findBySubjectSectionId(section.getId())) {
                conflictChecker.checkClassroomAvailability(cycle.getTerm(),
                        slot.getClassroom().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
            }
        }

        var courseSections = courseSectionRepository.findByAcademicOfferingCycleId(cycleId);
        for (var section : courseSections) {
            for (var slot : slotRepository.findByCourseSectionId(section.getId())) {
                conflictChecker.checkClassroomAvailability(cycle.getTerm(),
                        slot.getClassroom().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
            }
        }

        subjectSections.forEach(s -> { s.setStatus(StatusType.ACTIVE); subjectSectionRepository.save(s); });
        courseSections.forEach(s -> { s.setStatus(StatusType.ACTIVE); courseSectionRepository.save(s); });

        cycle.approve(currentPersonService.resolveCurrentPerson(), LocalDateTime.now());
        cycleRepository.save(cycle);
    }
}
