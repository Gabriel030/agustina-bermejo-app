package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.ShiftRangeRequest;
import com.red_escuela.backend.academicstructure.exception.InvalidShiftRangesException;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftRangeUpdaterTest {

    @Mock
    private ShiftRangeRepository shiftRangeRepository;

    private ShiftRangeUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new ShiftRangeUpdater(shiftRangeRepository);
    }

    private static ShiftRangeRequest range(SectionShift shift, int startHour, int startMinute, int endHour, int endMinute) {
        return new ShiftRangeRequest(shift, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }

    private static List<ShiftRangeRequest> valid() {
        return List.of(
                range(SectionShift.MORNING, 8, 0, 12, 30),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0),
                range(SectionShift.EVENING, 18, 30, 22, 30));
    }

    @Test
    void coherentRangesAreSaved() {
        when(shiftRangeRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        var saved = updater.replaceRanges(valid());

        assertThat(saved).hasSize(3);
        verify(shiftRangeRepository).saveAll(anyList());
    }

    @Test
    void touchingRangesWithoutGapAreAccepted() {
        when(shiftRangeRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        var saved = updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 8, 0, 13, 0),
                range(SectionShift.AFTERNOON, 13, 0, 18, 0),
                range(SectionShift.EVENING, 18, 0, 22, 0)));

        assertThat(saved).hasSize(3);
    }

    @Test
    void overlappingRangesAreRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 8, 0, 13, 31),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0),
                range(SectionShift.EVENING, 18, 30, 22, 30))))
                .isInstanceOf(InvalidShiftRangesException.class);

        verify(shiftRangeRepository, never()).saveAll(anyList());
    }

    @Test
    void rangeWithStartEqualToEndIsRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 8, 0, 8, 0),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0),
                range(SectionShift.EVENING, 18, 30, 22, 30))))
                .isInstanceOf(InvalidShiftRangesException.class);
    }

    @Test
    void rangeWithStartAfterEndIsRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 12, 0, 8, 0),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0),
                range(SectionShift.EVENING, 18, 30, 22, 30))))
                .isInstanceOf(InvalidShiftRangesException.class);
    }

    @Test
    void missingShiftIsRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 8, 0, 12, 30),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0))))
                .isInstanceOf(InvalidShiftRangesException.class);
    }

    @Test
    void repeatedShiftIsRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 8, 0, 12, 30),
                range(SectionShift.MORNING, 13, 30, 18, 0),
                range(SectionShift.EVENING, 18, 30, 22, 30))))
                .isInstanceOf(InvalidShiftRangesException.class);
    }

    @Test
    void shiftsOutOfChronologicalOrderAreRejected() {
        assertThatThrownBy(() -> updater.replaceRanges(List.of(
                range(SectionShift.MORNING, 18, 30, 22, 30),
                range(SectionShift.AFTERNOON, 13, 30, 18, 0),
                range(SectionShift.EVENING, 8, 0, 12, 30))))
                .isInstanceOf(InvalidShiftRangesException.class);
    }
}
