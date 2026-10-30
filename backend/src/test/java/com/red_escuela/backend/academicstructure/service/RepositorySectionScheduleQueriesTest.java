package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySectionScheduleQueriesTest {

    @Mock
    private SectionScheduleSlotRepository slotRepository;
    @InjectMocks
    private RepositorySectionScheduleQueries queries;

    @Test
    void findProgramSectionSlotsMapsDayTimesAndClassroomName() {
        var classroom = new Classroom();
        classroom.setName("Aula 3");
        var slot = new SectionScheduleSlot();
        slot.setDayOfWeek(DayOfWeek.TUESDAY);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setClassroom(classroom);
        when(slotRepository.findBySubjectSectionId(9L)).thenReturn(List.of(slot));

        var slots = queries.findProgramSectionSlots(9L);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(slots.get(0).classroomName()).isEqualTo("Aula 3");
    }
}
