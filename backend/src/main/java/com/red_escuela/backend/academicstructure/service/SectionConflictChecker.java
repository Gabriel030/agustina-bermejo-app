package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Component
public class SectionConflictChecker {

    private final SectionScheduleSlotRepository slotRepository;

    public SectionConflictChecker(SectionScheduleSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public void checkClassroomAvailability(Long classroomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        checkClassroomAvailability(classroomId, dayOfWeek, startTime, endTime, null);
    }

    public void checkClassroomAvailability(Long classroomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Long excludeSlotId) {
        var conflicts = slotRepository.findConflictingSlots(classroomId, dayOfWeek, startTime, endTime, excludeSlotId);
        if (!conflicts.isEmpty()) {
            throw new SectionScheduleConflictException(classroomId, dayOfWeek, startTime, endTime);
        }
    }
}
