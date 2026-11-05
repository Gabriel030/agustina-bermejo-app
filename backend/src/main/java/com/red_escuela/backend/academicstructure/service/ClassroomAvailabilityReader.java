package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ClassroomAvailabilityReader {

    static final long INFORMATIVE_WINDOW_DAYS = 366;

    private static final Comparator<ClassroomSlotOccupancyResponse> MOST_RELEVANT = Comparator
            .comparing(ClassroomSlotOccupancyResponse::overlapsInDates)
            .thenComparing(occupant -> occupant.term().endDate());

    private final ClassroomRepository classroomRepository;
    private final TermFinder termFinder;
    private final SectionConflictChecker conflictChecker;
    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomSlotOccupancyAssembler occupancyAssembler;

    public ClassroomAvailabilityReader(
            ClassroomRepository classroomRepository,
            TermFinder termFinder,
            SectionConflictChecker conflictChecker,
            SectionScheduleSlotRepository slotRepository,
            ClassroomSlotOccupancyAssembler occupancyAssembler
    ) {
        this.classroomRepository = classroomRepository;
        this.termFinder = termFinder;
        this.conflictChecker = conflictChecker;
        this.slotRepository = slotRepository;
        this.occupancyAssembler = occupancyAssembler;
    }

    @Transactional(readOnly = true)
    public List<ClassroomAvailabilityResponse> readAvailability(Long termId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer seats) {
        Term term = termFinder.findOrThrow(termId);
        Set<Long> occupiedIds = conflictChecker.occupiedClassroomIds(term, dayOfWeek, startTime, endTime);
        Map<Long, ClassroomSlotOccupancyResponse> occupants = mostRelevantOccupants(term, dayOfWeek, startTime, endTime);
        return classroomRepository.findByStatus(StatusType.ACTIVE)
                .stream()
                .map(c -> ClassroomAvailabilityResponse.of(c, !occupiedIds.contains(c.getId()), seats, occupants.get(c.getId())))
                .toList();
    }

    private Map<Long, ClassroomSlotOccupancyResponse> mostRelevantOccupants(Term term, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        List<SectionScheduleSlot> slots = slotRepository.findOccupantSlotsInWindow(
                term.getStartDate().minusDays(INFORMATIVE_WINDOW_DAYS), term.getEndDate().plusDays(INFORMATIVE_WINDOW_DAYS),
                dayOfWeek, startTime, endTime);
        Map<Long, ClassroomSlotOccupancyResponse> byClassroom = new HashMap<>();
        for (SectionScheduleSlot slot : slots) {
            ClassroomSlotOccupancyResponse occupant = occupancyAssembler.assemble(slot, term);
            byClassroom.merge(slot.getClassroom().getId(), occupant, (a, b) -> MOST_RELEVANT.compare(a, b) >= 0 ? a : b);
        }
        return byClassroom;
    }
}
