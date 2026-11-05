package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record StaffAssignmentSlotResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String classroomName) {

    public static StaffAssignmentSlotResponse from(SectionScheduleSlot slot) {
        return new StaffAssignmentSlotResponse(
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName());
    }
}
