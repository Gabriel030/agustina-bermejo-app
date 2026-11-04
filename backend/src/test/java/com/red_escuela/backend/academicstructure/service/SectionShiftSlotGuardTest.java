package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;
import com.red_escuela.backend.academicstructure.exception.SlotOutsideShiftException;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionShiftSlotGuardTest {

    @Mock
    private ShiftRangeRepository shiftRangeRepository;

    private SectionShiftSlotGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SectionShiftSlotGuard(shiftRangeRepository);
    }

    private void morningRange() {
        when(shiftRangeRepository.findById(SectionShift.MORNING))
                .thenReturn(Optional.of(new ShiftRange(SectionShift.MORNING, LocalTime.of(8, 0), LocalTime.of(12, 30))));
    }

    @Test
    void slotInsideTheShiftIsAccepted() {
        morningRange();

        assertThatCode(() -> guard.ensureInsideShift(SectionShift.MORNING, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void slotTouchingBothBordersOfTheShiftIsAccepted() {
        morningRange();

        assertThatCode(() -> guard.ensureInsideShift(SectionShift.MORNING, LocalTime.of(8, 0), LocalTime.of(12, 30)))
                .doesNotThrowAnyException();
    }

    @Test
    void slotStartingBeforeTheShiftIsRejected() {
        morningRange();

        assertThatThrownBy(() -> guard.ensureInsideShift(SectionShift.MORNING, LocalTime.of(7, 59), LocalTime.of(10, 0)))
                .isInstanceOf(SlotOutsideShiftException.class);
    }

    @Test
    void slotEndingAfterTheShiftIsRejected() {
        morningRange();

        assertThatThrownBy(() -> guard.ensureInsideShift(SectionShift.MORNING, LocalTime.of(11, 0), LocalTime.of(12, 31)))
                .isInstanceOf(SlotOutsideShiftException.class);
    }

    @Test
    void sectionWithoutShiftSkipsTheCheck() {
        assertThatCode(() -> guard.ensureInsideShift(null, LocalTime.of(2, 0), LocalTime.of(3, 0)))
                .doesNotThrowAnyException();

        verifyNoInteractions(shiftRangeRepository);
    }
}
