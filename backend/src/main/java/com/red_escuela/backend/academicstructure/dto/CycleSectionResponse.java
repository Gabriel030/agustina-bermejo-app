package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
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
        List<SlotInfo> slots,
        PendingModificationInfo pendingModification,
        String lastRejectionReason
) {
    public record InstructorInfo(String name, String role) {}
    public record SlotInfo(String dayOfWeek, String startTime, String endTime, Long classroomId, String classroomName) {}

    public record PendingModificationInfo(boolean replacesSchedule, String requestedAt, List<SlotInfo> slots) {}

    public static CycleSectionResponse fromSubjectSection(SubjectSection s, List<SectionScheduleSlot> slots, SectionModificationProposal latestProposal) {
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
                slotInfos,
                pendingModificationOf(latestProposal),
                lastRejectionReasonOf(latestProposal));
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
                slotInfos,
                null,
                null);
    }

    private static PendingModificationInfo pendingModificationOf(SectionModificationProposal proposal) {
        if (proposal == null || !proposal.isPending()) {
            return null;
        }
        var proposed = proposal.getSlots().stream()
                .map(sl -> new SlotInfo(
                        sl.getDayOfWeek().name(),
                        sl.getStartTime().toString(),
                        sl.getEndTime().toString(),
                        sl.getClassroom().getId(),
                        sl.getClassroom().getName()))
                .toList();
        return new PendingModificationInfo(proposal.isReplacesSchedule(), proposal.getRequestedAt().toString(), proposed);
    }

    private static String lastRejectionReasonOf(SectionModificationProposal proposal) {
        if (proposal == null || proposal.getStatus() != com.red_escuela.backend.academicstructure.entity.SectionModificationStatus.REJECTED) {
            return null;
        }
        return proposal.getRejectionReason();
    }
}
