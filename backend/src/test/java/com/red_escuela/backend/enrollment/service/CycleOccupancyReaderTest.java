package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CycleSectionRef;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleOccupancyReaderTest {

    @Mock
    private SectionFinder sectionFinder;
    @Mock
    private SectionOccupancyCounter occupancyCounter;
    @InjectMocks
    private CycleOccupancyReader service;

    @Test
    void reportsSeatsTakenAndAvailableForEachSection() {
        when(sectionFinder.listSectionsOfCycle(12L)).thenReturn(List.of(
                new CycleSectionRef(SectionKind.PROGRAM, 5L, "Comisión A", 30),
                new CycleSectionRef(SectionKind.PROGRAM, 6L, "Comisión B", 10)));
        when(occupancyCounter.countSeatsTaken(SectionKind.PROGRAM, 5L)).thenReturn(28L);
        when(occupancyCounter.countSeatsTaken(SectionKind.PROGRAM, 6L)).thenReturn(3L);
        when(occupancyCounter.countWaitlisted(SectionKind.PROGRAM, 5L)).thenReturn(2L);
        when(occupancyCounter.countWaitlisted(SectionKind.PROGRAM, 6L)).thenReturn(0L);

        var response = service.readOccupancy(12L);

        assertThat(response.cycleId()).isEqualTo(12L);
        assertThat(response.sections()).extracting("sectionId", "enrolled", "available", "waitlisted")
                .containsExactly(
                        tuple(5L, 28L, 2L, 2L),
                        tuple(6L, 3L, 7L, 0L));
    }

    @Test
    void sectionWithoutCapacityHasNoAvailableLimit() {
        when(sectionFinder.listSectionsOfCycle(1L))
                .thenReturn(List.of(new CycleSectionRef(SectionKind.COURSE, 9L, "Curso", null)));
        when(occupancyCounter.countSeatsTaken(SectionKind.COURSE, 9L)).thenReturn(4L);
        when(occupancyCounter.countWaitlisted(SectionKind.COURSE, 9L)).thenReturn(0L);

        var section = service.readOccupancy(1L).sections().get(0);

        assertThat(section.capacity()).isNull();
        assertThat(section.available()).isNull();
        assertThat(section.enrolled()).isEqualTo(4L);
    }

    @Test
    void overbookedSectionNeverReportsNegativeAvailability() {
        when(sectionFinder.listSectionsOfCycle(1L))
                .thenReturn(List.of(new CycleSectionRef(SectionKind.PROGRAM, 5L, "A", 2)));
        when(occupancyCounter.countSeatsTaken(SectionKind.PROGRAM, 5L)).thenReturn(3L);
        when(occupancyCounter.countWaitlisted(SectionKind.PROGRAM, 5L)).thenReturn(0L);

        assertThat(service.readOccupancy(1L).sections().get(0).available()).isZero();
    }

    @Test
    void cycleWithoutSectionsReturnsEmptyList() {
        when(sectionFinder.listSectionsOfCycle(1L)).thenReturn(List.of());

        assertThat(service.readOccupancy(1L).sections()).isEmpty();
    }

    @Test
    void unknownCycleThrowsCycleNotFound() {
        when(sectionFinder.listSectionsOfCycle(99L)).thenThrow(new CycleNotFoundException(99L));

        assertThatThrownBy(() -> service.readOccupancy(99L)).isInstanceOf(CycleNotFoundException.class);
    }
}
