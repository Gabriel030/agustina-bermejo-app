package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.dto.StaffAssignmentResponse;
import com.red_escuela.backend.academicstructure.dto.StaffAssignmentSlotResponse;
import com.red_escuela.backend.academicstructure.dto.StaffAssignmentsResponse;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.identity.api.StaffRecordAccessChecker;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StaffAssignmentLister {

    private final StaffRecordAccessChecker staffRecordAccessChecker;
    private final InstructorRepository instructorRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final SectionScheduleSlotRepository sectionScheduleSlotRepository;

    public StaffAssignmentLister(StaffRecordAccessChecker staffRecordAccessChecker,
                                       InstructorRepository instructorRepository,
                                       SubjectSectionInstructorRepository subjectSectionInstructorRepository,
                                       CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
                                       SectionScheduleSlotRepository sectionScheduleSlotRepository) {
        this.staffRecordAccessChecker = staffRecordAccessChecker;
        this.instructorRepository = instructorRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.sectionScheduleSlotRepository = sectionScheduleSlotRepository;
    }

    public StaffAssignmentsResponse list(Long staffPersonId, boolean includeClosed) {
        staffRecordAccessChecker.requireCanView(staffPersonId);
        Optional<Instructor> instructor = instructorRepository.findByPerson_Id(staffPersonId);
        if (instructor.isEmpty()) {
            return new StaffAssignmentsResponse(List.of(), 0);
        }
        Long instructorId = instructor.get().getId();
        List<SubjectSectionInstructor> programLinks = subjectSectionInstructorRepository
                .findDetailedByInstructorId(instructorId).stream()
                .filter(link -> link.getSection().isOfferingApproved())
                .filter(link -> includeClosed || link.getSection().getStatus() == StatusType.ACTIVE)
                .toList();
        List<CourseSubjectSectionInstructor> courseLinks = courseSubjectSectionInstructorRepository
                .findDetailedByInstructorId(instructorId).stream()
                .filter(link -> link.getSection().isOfferingApproved())
                .filter(link -> includeClosed || link.getSection().getStatus() == StatusType.ACTIVE)
                .toList();

        Map<Long, List<SectionScheduleSlot>> programSlots = programLinks.isEmpty() ? Map.of()
                : sectionScheduleSlotRepository.findWithClassroomBySubjectSectionIdIn(
                        programLinks.stream().map(link -> link.getSection().getId()).toList()).stream()
                .collect(Collectors.groupingBy(slot -> slot.getSubjectSection().getId()));
        Map<Long, List<SectionScheduleSlot>> courseSlots = courseLinks.isEmpty() ? Map.of()
                : sectionScheduleSlotRepository.findWithClassroomByCourseSectionIdIn(
                        courseLinks.stream().map(link -> link.getSection().getId()).toList()).stream()
                .collect(Collectors.groupingBy(slot -> slot.getCourseSection().getId()));

        List<StaffAssignmentResponse> assignments = new java.util.ArrayList<>();
        programLinks.forEach(link -> assignments.add(new StaffAssignmentResponse(
                link.getSection().getId(), SectionKind.PROGRAM, link.getSection().getSubject().getName(),
                link.getSection().getName(), link.getRole(), link.getHoursPerWeek(),
                link.getSection().getTerm().getDescription(), link.getSection().getTerm().getStartDate(),
                link.getSection().getTerm().getEndDate(), link.getSection().getStatus(),
                toSlots(programSlots.get(link.getSection().getId())))));
        courseLinks.forEach(link -> assignments.add(new StaffAssignmentResponse(
                link.getSection().getId(), SectionKind.COURSE, link.getSection().getCourseSubject().getName(),
                link.getSection().getName(), link.getRole(), link.getHoursPerWeek(),
                link.getSection().getTerm().getDescription(), link.getSection().getTerm().getStartDate(),
                link.getSection().getTerm().getEndDate(), link.getSection().getStatus(),
                toSlots(courseSlots.get(link.getSection().getId())))));
        assignments.sort(Comparator.comparing(StaffAssignmentResponse::termStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StaffAssignmentResponse::subjectName)
                .thenComparing(StaffAssignmentResponse::sectionName));
        int totalWeeklyHours = assignments.stream()
                .mapToInt(assignment -> assignment.hoursPerWeek() == null ? 0 : assignment.hoursPerWeek())
                .sum();
        return new StaffAssignmentsResponse(List.copyOf(assignments), totalWeeklyHours);
    }

    private List<StaffAssignmentSlotResponse> toSlots(List<SectionScheduleSlot> slots) {
        if (slots == null) {
            return List.of();
        }
        return slots.stream()
                .sorted(Comparator.comparing(SectionScheduleSlot::getDayOfWeek)
                        .thenComparing(SectionScheduleSlot::getStartTime))
                .map(StaffAssignmentSlotResponse::from)
                .toList();
    }
}
