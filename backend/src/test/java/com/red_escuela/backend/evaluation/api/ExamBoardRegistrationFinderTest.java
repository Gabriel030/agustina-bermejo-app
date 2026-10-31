package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamBoardRegistrationFinderTest {

    @Mock
    private ExamBoardRegistrationRepository examBoardRegistrationRepository;
    @InjectMocks
    private ExamBoardRegistrationFinder finder;

    @Test
    void requireByIdReturnsTheViewOfTheRegistration() {
        ExamBoard examBoard = mock(ExamBoard.class);
        when(examBoard.getId()).thenReturn(9L);
        ExamBoardRegistration registration = mock(ExamBoardRegistration.class);
        when(registration.getId()).thenReturn(4L);
        when(registration.getExamBoard()).thenReturn(examBoard);
        when(examBoardRegistrationRepository.findById(4L)).thenReturn(Optional.of(registration));

        assertThat(finder.requireById(4L)).isEqualTo(new ExamBoardRegistrationView(4L, 9L));
    }

    @Test
    void requireByIdFailsWhenTheRegistrationDoesNotExist() {
        when(examBoardRegistrationRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requireById(4L)).isInstanceOf(ExamBoardRegistrationNotFoundException.class);
    }
}
