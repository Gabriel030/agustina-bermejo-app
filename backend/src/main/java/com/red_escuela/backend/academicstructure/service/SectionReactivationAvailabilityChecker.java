package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionReactivationAvailabilityChecker {

    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionConflictChecker conflictChecker;
    private final SubjectSectionInstructorRepository subjectInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseInstructorRepository;

    public SectionReactivationAvailabilityChecker(SectionScheduleSlotRepository slotRepository,
                                                  ClassroomRepository classroomRepository,
                                                  SectionConflictChecker conflictChecker,
                                                  SubjectSectionInstructorRepository subjectInstructorRepository,
                                                  CourseSubjectSectionInstructorRepository courseInstructorRepository) {
        this.slotRepository = slotRepository;
        this.classroomRepository = classroomRepository;
        this.conflictChecker = conflictChecker;
        this.subjectInstructorRepository = subjectInstructorRepository;
        this.courseInstructorRepository = courseInstructorRepository;
    }

    public void checkSubjectSectionCanBeReactivated(SubjectSection section) {
        List<SectionScheduleSlot> slots = slotRepository.findBySubjectSectionId(section.getId());
        checkClassroomsAvailability(section.getTerm(), slots);
        List<Long> instructorIds = subjectInstructorRepository.findBySectionId(section.getId()).stream()
                .map(link -> link.getInstructor().getId())
                .toList();
        checkInstructorsAvailability(section.getTerm(), instructorIds, slots);
    }

    public void checkCourseSectionCanBeReactivated(CourseSubjectSection section) {
        List<SectionScheduleSlot> slots = slotRepository.findByCourseSectionId(section.getId());
        checkClassroomsAvailability(section.getTerm(), slots);
        List<Long> instructorIds = courseInstructorRepository.findBySectionId(section.getId()).stream()
                .map(link -> link.getInstructor().getId())
                .toList();
        checkInstructorsAvailability(section.getTerm(), instructorIds, slots);
    }

    private void checkClassroomsAvailability(Term term, List<SectionScheduleSlot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        classroomRepository.lockAllByIdOrdered(slots.stream()
                .map(slot -> slot.getClassroom().getId())
                .collect(Collectors.toSet()));
        Set<Long> ownSlotIds = slots.stream().map(SectionScheduleSlot::getId).collect(Collectors.toSet());
        for (SectionScheduleSlot slot : slots) {
            conflictChecker.checkClassroomAvailabilityIgnoringSlots(
                    term, slot.getClassroom().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), ownSlotIds);
        }
    }

    private void checkInstructorsAvailability(Term term, List<Long> instructorIds, List<SectionScheduleSlot> slots) {
        for (Long instructorId : instructorIds) {
            conflictChecker.checkInstructorAvailability(term, instructorId, slots);
        }
    }
}
