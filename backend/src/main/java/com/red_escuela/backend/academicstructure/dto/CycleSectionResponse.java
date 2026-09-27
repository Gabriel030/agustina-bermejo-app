package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;

import java.util.List;

public record CycleSectionResponse(
        Long id,
        String name,
        String subjectName,
        Integer capacity,
        String status,
        boolean pendingApproval,
        List<InstructorInfo> instructors,
        List<SlotInfo> slots
) {
    public record InstructorInfo(String name, String role) {}
    public record SlotInfo(String dayOfWeek, String startTime, String endTime, Long classroomId, String classroomName) {}

    public static CycleSectionResponse fromSubjectSection(SubjectSection s, List<SectionScheduleSlot> slots) {
        var instructors = s.getInstructors().stream()
                .map(i -> new InstructorInfo(
                        i.getInstructor().getPerson().getName() + " " + i.getInstructor().getPerson().getLastname(),
                        i.getRole().name()))
                .toList();
        var slotInfos = slots.stream()
                .map(sl -> new SlotInfo(
                        sl.getDayOfWeek().name(),
                        sl.getStartTime().toString(),
                        sl.getEndTime().toString(),
                        sl.getClassroom().getId(),
                        sl.getClassroom().getName()))
                .toList();
        return new CycleSectionResponse(
                s.getId(),
                s.getName(),
                s.getSubject().getName(),
                s.getCapacity(),
                s.getStatus().name(),
                s.isPendingApproval(),
                instructors,
                slotInfos);
    }

    public static CycleSectionResponse fromCourseSection(CourseSubjectSection s, List<SectionScheduleSlot> slots) {
        var instructors = s.getInstructors().stream()
                .map(i -> new InstructorInfo(
                        i.getInstructor().getPerson().getName() + " " + i.getInstructor().getPerson().getLastname(),
                        i.getRole().name()))
                .toList();
        var slotInfos = slots.stream()
                .map(sl -> new SlotInfo(
                        sl.getDayOfWeek().name(),
                        sl.getStartTime().toString(),
                        sl.getEndTime().toString(),
                        sl.getClassroom().getId(),
                        sl.getClassroom().getName()))
                .toList();
        return new CycleSectionResponse(
                s.getId(),
                s.getName(),
                s.getCourseSubject().getName(),
                s.getCapacity(),
                s.getStatus().name(),
                s.isPendingApproval(),
                instructors,
                slotInfos);
    }
}
