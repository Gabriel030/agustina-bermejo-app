package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@UseCase
@Transactional(readOnly = true)
public class GetClassroomAvailabilityService implements GetClassroomAvailabilityUseCase {

    private final ClassroomRepository classroomRepository;
    private final SectionScheduleSlotRepository slotRepository;

    public GetClassroomAvailabilityService(ClassroomRepository classroomRepository, SectionScheduleSlotRepository slotRepository) {
        this.classroomRepository = classroomRepository;
        this.slotRepository = slotRepository;
    }

    @Override
    public List<ClassroomAvailabilityResponse> getAvailability(Long termId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        Set<Long> occupiedIds = slotRepository.findOccupiedClassroomIds(termId, dayOfWeek, startTime, endTime);
        return classroomRepository.findByStatus(StatusType.ACTIVE)
                .stream()
                .map(c -> ClassroomAvailabilityResponse.of(c, !occupiedIds.contains(c.getId())))
                .toList();
    }
}
