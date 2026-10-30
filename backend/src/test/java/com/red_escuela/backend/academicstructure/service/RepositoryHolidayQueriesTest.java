package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryHolidayQueriesTest {

    @Mock
    private HolidayRepository holidayRepository;
    @InjectMocks
    private RepositoryHolidayQueries queries;

    @Test
    void isHolidayDelegatesToTheDateLookup() {
        when(holidayRepository.existsByDate(LocalDate.of(2027, 5, 25))).thenReturn(true);
        when(holidayRepository.existsByDate(LocalDate.of(2027, 5, 26))).thenReturn(false);

        assertThat(queries.isHoliday(LocalDate.of(2027, 5, 25))).isTrue();
        assertThat(queries.isHoliday(LocalDate.of(2027, 5, 26))).isFalse();
    }
}
