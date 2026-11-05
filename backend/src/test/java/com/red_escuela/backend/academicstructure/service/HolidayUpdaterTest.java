package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.exception.HolidayAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.HolidayNotFoundException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayUpdaterTest {

    private static final long ID = 7L;
    private static final LocalDate NEW_DATE = LocalDate.of(2030, 5, 2);

    @Mock
    private HolidayRepository holidayRepository;

    private HolidayUpdater updater;
    private Holiday holiday;

    @BeforeEach
    void setUp() {
        updater = new HolidayUpdater(holidayRepository);
        holiday = new Holiday();
        holiday.setId(ID);
        holiday.setDate(LocalDate.of(2030, 5, 1));
        holiday.setName("Original");
        holiday.setType(HolidayType.NATIONAL);
        holiday.setDescription("Vieja");
    }

    @Test
    void updateReplacesAllFieldsAndClearsDescriptionWhenAbsent() {
        when(holidayRepository.findById(ID)).thenReturn(Optional.of(holiday));
        when(holidayRepository.existsByDateAndIdNot(NEW_DATE, ID)).thenReturn(false);
        when(holidayRepository.saveAndFlush(holiday)).thenReturn(holiday);

        HolidayResponse response = updater.update(ID,
                new UpdateHolidayRequest(NEW_DATE, "Puente", HolidayType.BRIDGE, null));

        assertThat(response).isEqualTo(new HolidayResponse(ID, NEW_DATE, "Puente", HolidayType.BRIDGE, null));
    }

    @Test
    void updateToDateOfAnotherHolidayThrowsAndDoesNotSave() {
        when(holidayRepository.findById(ID)).thenReturn(Optional.of(holiday));
        when(holidayRepository.existsByDateAndIdNot(NEW_DATE, ID)).thenReturn(true);

        assertThatThrownBy(() -> updater.update(ID,
                new UpdateHolidayRequest(NEW_DATE, "Puente", HolidayType.BRIDGE, null)))
                .isInstanceOf(HolidayAlreadyExistsException.class);

        verify(holidayRepository, never()).saveAndFlush(any());
        assertThat(holiday.getName()).isEqualTo("Original");
    }

    @Test
    void updateOfMissingHolidayThrowsNotFound() {
        when(holidayRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updater.update(ID,
                new UpdateHolidayRequest(NEW_DATE, "Puente", HolidayType.BRIDGE, null)))
                .isInstanceOf(HolidayNotFoundException.class);
    }
}
