package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCurrencyFinderTest {

    @Mock
    private ProgramRepository programRepository;
    @Mock
    private CourseRepository courseRepository;
    @InjectMocks
    private OfferingCurrencyFinder finder;

    @Test
    void programCurrencyIsTheCurrencyOfTheProgram() {
        Program program = mock(Program.class);
        when(program.getCurrency()).thenReturn(Currency.USD);
        when(programRepository.findById(4L)).thenReturn(Optional.of(program));
        when(programRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(finder.findProgramCurrency(4L)).contains(Currency.USD);
        assertThat(finder.findProgramCurrency(5L)).isEmpty();
    }

    @Test
    void courseCurrencyIsTheCurrencyOfTheCourse() {
        Course course = mock(Course.class);
        when(course.getCurrency()).thenReturn(Currency.ARS);
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));
        when(courseRepository.findById(8L)).thenReturn(Optional.empty());

        assertThat(finder.findCourseCurrency(9L)).contains(Currency.ARS);
        assertThat(finder.findCourseCurrency(8L)).isEmpty();
    }
}
