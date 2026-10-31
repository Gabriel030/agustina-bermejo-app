package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Component
public class ClassroomAvailabilityReader {

    private final ClassroomRepository classroomRepository;
    private final TermFinder termFinder;
    private final SectionConflictChecker conflictChecker;

    public ClassroomAvailabilityReader(ClassroomRepository classroomRepository, TermFinder termFinder, SectionConflictChecker conflictChecker) {
        this.classroomRepository = classroomRepository;
        this.termFinder = termFinder;
        this.conflictChecker = conflictChecker;
    }

    @Transactional(readOnly = true)
    public List<ClassroomAvailabilityResponse> readAvailability(Long termId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        Set<Long> occupiedIds = conflictChecker.occupiedClassroomIds(termFinder.findOrThrow(termId), dayOfWeek, startTime, endTime);
        return classroomRepository.findByStatus(StatusType.ACTIVE)
                .stream()
                .map(c -> ClassroomAvailabilityResponse.of(c, !occupiedIds.contains(c.getId())))
                .toList();
    }
}
