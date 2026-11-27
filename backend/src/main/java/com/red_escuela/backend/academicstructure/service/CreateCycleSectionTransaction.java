package com.red_escuela.backend.academicstructure.service;

import org.springframework.dao.DataIntegrityViolationException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.api.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CreateCycleSectionTransaction {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionConflictChecker conflictChecker;
    private final CycleSectionAbsenceLimitAssigner absenceLimitAssigner;
    private final SectionIdentityAssigner identityAssigner;
    private final SectionShiftSlotGuard shiftSlotGuard;
    private final SectionEvaluationSetupPort evaluationSetupPort;

    public CreateCycleSectionTransaction(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectRepository subjectRepository,
            CourseSubjectRepository courseSubjectRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            ClassroomRepository classroomRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionConflictChecker conflictChecker,
            CycleSectionAbsenceLimitAssigner absenceLimitAssigner,
            SectionIdentityAssigner identityAssigner,
            SectionShiftSlotGuard shiftSlotGuard,
            SectionEvaluationSetupPort evaluationSetupPort
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.classroomRepository = classroomRepository;
        this.slotRepository = slotRepository;
        this.conflictChecker = conflictChecker;
        this.absenceLimitAssigner = absenceLimitAssigner;
        this.identityAssigner = identityAssigner;
        this.shiftSlotGuard = shiftSlotGuard;
        this.evaluationSetupPort = evaluationSetupPort;
    }

    @Transactional
    public Long createSection(Long cycleId, String name, Long subjectId, Long courseSubjectId, Integer capacity,
                              List<ScheduleSlotRequest> slots, Percentage maxAbsencePercent, SectionShift shift,
                              String description, LocalDateTime createdAt) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycle.ensureEditable();
        identityAssigner.requireShift(shift);
        lockRequestedClassrooms(slots);
        if (cycle.getProgram() != null) {
            return createSubjectSection(cycle, name, subjectId, capacity, slots, maxAbsencePercent, shift, description, createdAt);
        } else {
            return createCourseSection(cycle, name, courseSubjectId, capacity, slots, maxAbsencePercent, shift, description, createdAt);
        }
    }

    private void lockRequestedClassrooms(List<ScheduleSlotRequest> slots) {
        if (slots == null) return;
        classroomRepository.lockAllByIdOrdered(slots.stream()
                .map(ScheduleSlotRequest::classroomId)
                .collect(Collectors.toSet()));
    }

    private Long createSubjectSection(AcademicOfferingCycle cycle, String name, Long subjectId, Integer capacity,
                                      List<ScheduleSlotRequest> slots, Percentage maxAbsencePercent,
                                      SectionShift shift, String description, LocalDateTime createdAt) {
        var subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        subject.requireOpenableForSections();
        var section = new SubjectSection();
        section.setName(name);
        section.setSubject(subject);
        section.setTerm(cycle.getTerm());
        section.setCapacity(capacity);
        absenceLimitAssigner.assignToSubjectSection(section, maxAbsencePercent);
        section.setStatus(StatusType.INACTIVE);
        section.setCreatedAt(createdAt);
        section.setAcademicOfferingCycle(cycle);
        identityAssigner.identifyNewSubjectSection(section, shift, description);
        var saved = subjectSectionRepository.save(section);
        evaluationSetupPort.createInitialEvaluationDraft(saved);
        saveSubjectSlots(saved, slots, shift, capacity);
        return saved.getId();
    }

    private Long createCourseSection(AcademicOfferingCycle cycle, String name, Long courseSubjectId, Integer capacity,
                                     List<ScheduleSlotRequest> slots, Percentage maxAbsencePercent,
                                     SectionShift shift, String description, LocalDateTime createdAt) {
        var courseSubject = courseSubjectRepository.findById(courseSubjectId)
                .orElseThrow(() -> new CourseSubjectNotFoundException(courseSubjectId));
        var section = new CourseSubjectSection();
        section.setName(name);
        section.setCourseSubject(courseSubject);
        section.setTerm(cycle.getTerm());
        section.setCapacity(capacity);
        absenceLimitAssigner.assignToCourseSection(section, maxAbsencePercent);
        section.setStatus(StatusType.INACTIVE);
        section.setCreatedAt(createdAt);
        section.setAcademicOfferingCycle(cycle);
        identityAssigner.identifyNewCourseSection(section, shift, description);
        var saved = courseSectionRepository.save(section);
        evaluationSetupPort.createInitialEvaluationDraftForCourseSection(saved);
        saveCourseSlots(saved, slots, shift, capacity);
        return saved.getId();
    }

    private Classroom availableClassroom(Term term, ScheduleSlotRequest slotReq, Integer capacity) {
        var classroom = classroomRepository.findById(slotReq.classroomId())
                .orElseThrow(() -> new ClassroomNotFoundException(slotReq.classroomId()));
        classroom.ensureCanHost(capacity);
        conflictChecker.checkClassroomAvailability(term, slotReq.classroomId(), slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime());
        return classroom;
    }

    private void saveSubjectSlots(SubjectSection section, List<ScheduleSlotRequest> slots, SectionShift shift, Integer capacity) {
        if (slots == null) return;
        for (var slotReq : slots) {
            shiftSlotGuard.ensureInsideShift(shift, slotReq.startTime(), slotReq.endTime());
            var classroom = availableClassroom(section.getTerm(), slotReq, capacity);
            var slot = new SectionScheduleSlot();
            slot.setSubjectSection(section);
            slot.setClassroom(classroom);
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setStartTime(slotReq.startTime());
            slot.setEndTime(slotReq.endTime());
            saveSlot(slot, slotReq);
        }
    }

    private void saveCourseSlots(CourseSubjectSection section, List<ScheduleSlotRequest> slots, SectionShift shift, Integer capacity) {
        if (slots == null) return;
        for (var slotReq : slots) {
            shiftSlotGuard.ensureInsideShift(shift, slotReq.startTime(), slotReq.endTime());
            var classroom = availableClassroom(section.getTerm(), slotReq, capacity);
            var slot = new SectionScheduleSlot();
            slot.setCourseSection(section);
            slot.setClassroom(classroom);
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setStartTime(slotReq.startTime());
            slot.setEndTime(slotReq.endTime());
            saveSlot(slot, slotReq);
        }
    }

    private void saveSlot(SectionScheduleSlot slot, ScheduleSlotRequest slotReq) {
        try {
            slotRepository.saveAndFlush(slot);
        } catch (DataIntegrityViolationException e) {
            if (ScheduleExclusionViolation.isClassroomOverlap(e)) {
                throw new SectionScheduleConflictException(
                        slotReq.classroomId(), slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime());
            }
            throw e;
        }
    }
}
