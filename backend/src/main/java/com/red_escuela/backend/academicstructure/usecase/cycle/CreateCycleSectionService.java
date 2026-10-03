package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.*;
import com.red_escuela.backend.academicstructure.exception.*;
import com.red_escuela.backend.academicstructure.repository.*;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class CreateCycleSectionService implements CreateCycleSectionUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionConflictChecker conflictChecker;

    public CreateCycleSectionService(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectRepository subjectRepository,
            CourseSubjectRepository courseSubjectRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            ClassroomRepository classroomRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionConflictChecker conflictChecker
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.classroomRepository = classroomRepository;
        this.slotRepository = slotRepository;
        this.conflictChecker = conflictChecker;
    }

    @Override
    @Transactional
    public Long createSection(Long cycleId, CreateCycleSectionRequest request) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycle.ensureEditable();
        if (cycle.getProgram() != null) {
            return createSubjectSection(cycle, request);
        } else {
            return createCourseSection(cycle, request);
        }
    }

    private Long createSubjectSection(AcademicOfferingCycle cycle, CreateCycleSectionRequest request) {
        var subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));
        var section = new SubjectSection();
        section.setName(request.name());
        section.setSubject(subject);
        section.setTerm(cycle.getTerm());
        section.setCapacity(request.capacity());
        section.setStatus(StatusType.INACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        section.setAcademicOfferingCycle(cycle);
        var saved = subjectSectionRepository.save(section);
        saveSubjectSlots(saved, request);
        return saved.getId();
    }

    private Long createCourseSection(AcademicOfferingCycle cycle, CreateCycleSectionRequest request) {
        var courseSubject = courseSubjectRepository.findById(request.courseSubjectId())
                .orElseThrow(() -> new CourseSubjectNotFoundException(request.courseSubjectId()));
        var section = new CourseSubjectSection();
        section.setName(request.name());
        section.setCourseSubject(courseSubject);
        section.setTerm(cycle.getTerm());
        section.setCapacity(request.capacity());
        section.setStatus(StatusType.INACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        section.setAcademicOfferingCycle(cycle);
        var saved = courseSectionRepository.save(section);
        saveCourseSlots(saved, request);
        return saved.getId();
    }

    private Classroom availableClassroom(Term term, ScheduleSlotRequest slotReq, Integer capacity) {
        var classroom = classroomRepository.findById(slotReq.classroomId())
                .orElseThrow(() -> new ClassroomNotFoundException(slotReq.classroomId()));
        classroom.ensureCanHost(capacity);
        conflictChecker.checkClassroomAvailability(term, slotReq.classroomId(), slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime());
        return classroom;
    }

    private void saveSubjectSlots(SubjectSection section, CreateCycleSectionRequest request) {
        if (request.slots() == null) return;
        for (var slotReq : request.slots()) {
            var classroom = availableClassroom(section.getTerm(), slotReq, request.capacity());
            var slot = new SectionScheduleSlot();
            slot.setSubjectSection(section);
            slot.setClassroom(classroom);
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setStartTime(slotReq.startTime());
            slot.setEndTime(slotReq.endTime());
            slotRepository.save(slot);
        }
    }

    private void saveCourseSlots(CourseSubjectSection section, CreateCycleSectionRequest request) {
        if (request.slots() == null) return;
        for (var slotReq : request.slots()) {
            var classroom = availableClassroom(section.getTerm(), slotReq, request.capacity());
            var slot = new SectionScheduleSlot();
            slot.setCourseSection(section);
            slot.setClassroom(classroom);
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setStartTime(slotReq.startTime());
            slot.setEndTime(slotReq.endTime());
            slotRepository.save(slot);
        }
    }
}
