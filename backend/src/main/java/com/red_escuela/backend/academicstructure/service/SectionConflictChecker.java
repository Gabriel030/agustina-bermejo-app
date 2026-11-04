package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionConflictChecker {

    private final SectionScheduleSlotRepository slotRepository;
    private final SubjectSectionInstructorRepository subjectInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseInstructorRepository;

    public SectionConflictChecker(SectionScheduleSlotRepository slotRepository,
                                  SubjectSectionInstructorRepository subjectInstructorRepository,
                                  CourseSubjectSectionInstructorRepository courseInstructorRepository) {
        this.slotRepository = slotRepository;
        this.subjectInstructorRepository = subjectInstructorRepository;
        this.courseInstructorRepository = courseInstructorRepository;
    }

    public void checkInstructorAvailability(Term term, Long instructorId, List<SectionScheduleSlot> slots) {
        Set<Long> ownSlotIds = slots.stream().map(SectionScheduleSlot::getId).collect(Collectors.toSet());
        for (SectionScheduleSlot slot : slots) {
            checkInstructorAvailabilityIgnoringSlots(
                    term, instructorId, slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), ownSlotIds);
        }
    }

    public void checkSectionInstructorsAvailability(Term term, Long subjectSectionId, DayOfWeek dayOfWeek,
                                                    LocalTime startTime, LocalTime endTime, Set<Long> ignoredSlotIds) {
        for (SubjectSectionInstructor link : subjectInstructorRepository.findBySectionId(subjectSectionId)) {
            checkInstructorAvailabilityIgnoringSlots(
                    term, link.getInstructor().getId(), dayOfWeek, startTime, endTime, ignoredSlotIds);
        }
    }

    public void checkSubjectSlotInstructorsAvailability(AcademicOfferingCycle cycle, SubjectSection section, SectionScheduleSlot slot) {
        checkSectionInstructorsAvailability(cycle.getTerm(), section.getId(), slot.getDayOfWeek(),
                slot.getStartTime(), slot.getEndTime(), Set.of(slot.getId()));
    }

    public void checkCourseSlotInstructorsAvailability(AcademicOfferingCycle cycle, CourseSubjectSection section, SectionScheduleSlot slot) {
        checkCourseSectionInstructorsAvailability(cycle.getTerm(), section.getId(), slot.getDayOfWeek(),
                slot.getStartTime(), slot.getEndTime(), Set.of(slot.getId()));
    }

    private void checkCourseSectionInstructorsAvailability(Term term, Long courseSectionId, DayOfWeek dayOfWeek,
                                                          LocalTime startTime, LocalTime endTime, Set<Long> ignoredSlotIds) {
        for (CourseSubjectSectionInstructor link : courseInstructorRepository.findBySectionId(courseSectionId)) {
            checkInstructorAvailabilityIgnoringSlots(
                    term, link.getInstructor().getId(), dayOfWeek, startTime, endTime, ignoredSlotIds);
        }
    }

    private void checkInstructorAvailabilityIgnoringSlots(Term term, Long instructorId, DayOfWeek dayOfWeek,
                                                          LocalTime startTime, LocalTime endTime, Set<Long> ignoredSlotIds) {
        boolean conflicts = slotRepository.findInstructorOccupyingSlots(
                        instructorId, term.getId(), term.getStartDate(), term.getEndDate(), dayOfWeek, startTime, endTime)
                .stream()
                .anyMatch(slot -> !ignoredSlotIds.contains(slot.getId()));
        if (conflicts) {
            throw new InstructorScheduleConflictException(instructorId, dayOfWeek, startTime, endTime);
        }
    }

    public void checkClassroomAvailability(Term term, Long classroomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        checkClassroomAvailability(term, classroomId, dayOfWeek, startTime, endTime, null);
    }

    public void checkClassroomAvailability(Term term, Long classroomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Long excludeSlotId) {
        boolean conflicts = occupyingSlots(term, dayOfWeek, startTime, endTime).stream()
                .filter(slot -> !slot.getId().equals(excludeSlotId))
                .anyMatch(slot -> slot.getClassroom().getId().equals(classroomId));
        if (conflicts) {
            throw new SectionScheduleConflictException(classroomId, dayOfWeek, startTime, endTime);
        }
    }

    public void checkClassroomAvailabilityIgnoringSlots(Term term, Long classroomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Set<Long> ignoredSlotIds) {
        boolean conflicts = occupyingSlots(term, dayOfWeek, startTime, endTime).stream()
                .filter(slot -> !ignoredSlotIds.contains(slot.getId()))
                .anyMatch(slot -> slot.getClassroom().getId().equals(classroomId));
        if (conflicts) {
            throw new SectionScheduleConflictException(classroomId, dayOfWeek, startTime, endTime);
        }
    }

    public Set<Long> occupiedClassroomIds(Term term, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return occupyingSlots(term, dayOfWeek, startTime, endTime).stream()
                .map(slot -> slot.getClassroom().getId())
                .collect(Collectors.toSet());
    }

    private List<SectionScheduleSlot> occupyingSlots(Term term, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return slotRepository.findOccupyingSlots(
                term.getId(), term.getStartDate(), term.getEndDate(), dayOfWeek, startTime, endTime);
    }
}
