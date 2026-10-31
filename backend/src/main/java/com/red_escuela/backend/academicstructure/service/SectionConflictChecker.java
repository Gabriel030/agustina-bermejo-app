package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionConflictChecker {

    private final SectionScheduleSlotRepository slotRepository;

    public SectionConflictChecker(SectionScheduleSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
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
