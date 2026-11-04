package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ClassroomOccupancyResponse;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.shared.domain.InvalidDateRangeException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.time.temporal.ChronoUnit;

@Component
public class ClassroomOccupancyReader {

    static final long MAX_RANGE_DAYS = 1830;

    private final ClassroomRepository classroomRepository;
    private final TermFinder termFinder;
    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomSlotOccupancyAssembler occupancyAssembler;

    public ClassroomOccupancyReader(
            ClassroomRepository classroomRepository,
            TermFinder termFinder,
            SectionScheduleSlotRepository slotRepository,
            ClassroomSlotOccupancyAssembler occupancyAssembler
    ) {
        this.classroomRepository = classroomRepository;
        this.termFinder = termFinder;
        this.slotRepository = slotRepository;
        this.occupancyAssembler = occupancyAssembler;
    }

    @Transactional(readOnly = true)
    public ClassroomOccupancyResponse readOccupancy(Long classroomId, Long termId, LocalDate from, LocalDate to) {
        Classroom classroom = classroomRepository.findById(classroomId).orElseThrow(() -> new ClassroomNotFoundException(classroomId));
        Term term = termFinder.findOrThrow(termId);
        LocalDate rangeStart = from != null ? from : term.getStartDate().toLocalDate().minusDays(ClassroomAvailabilityReader.INFORMATIVE_WINDOW_DAYS);
        LocalDate rangeEnd = to != null ? to : term.getEndDate().toLocalDate().plusDays(ClassroomAvailabilityReader.INFORMATIVE_WINDOW_DAYS);
        if (rangeEnd.isBefore(rangeStart) || ChronoUnit.DAYS.between(rangeStart, rangeEnd) > MAX_RANGE_DAYS) {
            throw new InvalidDateRangeException(rangeStart, rangeEnd);
        }
        var slots = slotRepository.findClassroomSlotsInWindow(classroomId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay().minusNanos(1))
                .stream()
                .map(slot -> occupancyAssembler.assemble(slot, term))
                .sorted(Comparator.comparing(ClassroomSlotOccupancyResponse::dayOfWeek).thenComparing(ClassroomSlotOccupancyResponse::startTime))
                .toList();
        return new ClassroomOccupancyResponse(classroom.getId(), classroom.getName(), classroom.getCapacity(), term.getId(), rangeStart, rangeEnd, slots);
    }
}
