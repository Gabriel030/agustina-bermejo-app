package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CycleConflictsResponse;
import com.red_escuela.backend.academicstructure.dto.CycleConflictsResponse.Conflict;
import com.red_escuela.backend.academicstructure.dto.CycleConflictsResponse.Side;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CycleConflictFinder {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final SectionScheduleSlotRepository slotRepository;

    public CycleConflictFinder(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            SectionScheduleSlotRepository slotRepository
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public CycleConflictsResponse find(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        var found = new LinkedHashMap<String, Conflict>();

        for (var section : subjectSectionRepository.findByAcademicOfferingCycleId(cycleId)) {
            var own = new Side(section.getId(), section.getName(), section.getSubject().getName(), cycleId);
            var instructors = section.getInstructors().stream().map(i -> i.getInstructor()).toList();
            for (var slot : slotRepository.findBySubjectSectionId(section.getId())) {
                collect(found, cycle, slot, own, instructors);
            }
        }
        for (var section : courseSectionRepository.findByAcademicOfferingCycleId(cycleId)) {
            var own = new Side(section.getId(), section.getName(), section.getCourseSubject().getName(), cycleId);
            var instructors = section.getInstructors().stream().map(i -> i.getInstructor()).toList();
            for (var slot : slotRepository.findByCourseSectionId(section.getId())) {
                collect(found, cycle, slot, own, instructors);
            }
        }

        var conflicts = new ArrayList<>(found.values());
        return new CycleConflictsResponse(!conflicts.isEmpty(), conflicts);
    }

    private void collect(Map<String, Conflict> found, AcademicOfferingCycle cycle, SectionScheduleSlot slot, Side own, List<Instructor> instructors) {
        var term = cycle.getTerm();
        for (var other : slotRepository.findOccupyingSlots(term.getId(), term.getStartDate(), term.getEndDate(),
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime())) {
            if (!other.getId().equals(slot.getId()) && other.getClassroom().getId().equals(slot.getClassroom().getId())) {
                found.putIfAbsent(key("CLASSROOM", slot, other, null), conflict("CLASSROOM", slot, own, other, null));
            }
        }
        for (var instructor : instructors) {
            for (var other : slotRepository.findInstructorOccupyingSlots(instructor.getId(), term.getId(), term.getStartDate(),
                    term.getEndDate(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime())) {
                if (!other.getId().equals(slot.getId())) {
                    found.putIfAbsent(key("INSTRUCTOR", slot, other, instructor), conflict("INSTRUCTOR", slot, own, other, instructor));
                }
            }
        }
    }

    private String key(String type, SectionScheduleSlot slot, SectionScheduleSlot other, Instructor instructor) {
        var low = Math.min(slot.getId(), other.getId());
        var high = Math.max(slot.getId(), other.getId());
        return type + "|" + low + "|" + high + "|" + (instructor != null ? instructor.getId() : "");
    }

    private Conflict conflict(String type, SectionScheduleSlot slot, Side own, SectionScheduleSlot other, Instructor instructor) {
        return new Conflict(
                type,
                slot.getDayOfWeek().name(),
                slot.getStartTime().toString(),
                slot.getEndTime().toString(),
                slot.getClassroom().getId(),
                slot.getClassroom().getName(),
                instructor != null ? instructor.getId() : null,
                instructor != null ? instructor.getPerson().getName() + " " + instructor.getPerson().getLastname() : null,
                own,
                otherSide(other));
    }

    private Side otherSide(SectionScheduleSlot other) {
        if (other.getSubjectSection() != null) {
            var section = other.getSubjectSection();
            var cycle = section.getAcademicOfferingCycle();
            return new Side(section.getId(), section.getName(), section.getSubject().getName(), cycle != null ? cycle.getId() : null);
        }
        var section = other.getCourseSection();
        var cycle = section.getAcademicOfferingCycle();
        return new Side(section.getId(), section.getName(), section.getCourseSubject().getName(), cycle != null ? cycle.getId() : null);
    }
}
