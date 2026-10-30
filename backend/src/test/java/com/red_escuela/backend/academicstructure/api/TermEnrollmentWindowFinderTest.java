package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermEnrollmentWindowFinderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 12, 0);

    @Mock
    private TermRepository termRepository;
    @InjectMocks
    private TermEnrollmentWindowFinder finder;

    private Term termWithWindow(LocalDateTime start, LocalDateTime end, TermEnrollmentOverride override) {
        Term term = new Term();
        term.setId(7L);
        term.setEnrollmentStart(start);
        term.setEnrollmentEnd(end);
        term.setEnrollmentOverride(override);
        return term;
    }

    @Test
    void windowContainingTheMomentIsOpen() {
        when(termRepository.findById(7L)).thenReturn(Optional.of(termWithWindow(NOW.minusDays(1), NOW.plusDays(1), null)));

        TermEnrollmentWindowView view = finder.findEnrollmentWindow(7L, NOW);

        assertThat(view).isEqualTo(new TermEnrollmentWindowView(7L, false, false, true));
    }

    @Test
    void windowEndedBeforeTheMomentIsClosed() {
        when(termRepository.findById(7L)).thenReturn(Optional.of(termWithWindow(NOW.minusDays(5), NOW.minusDays(1), null)));

        assertThat(finder.findEnrollmentWindow(7L, NOW)).isEqualTo(new TermEnrollmentWindowView(7L, false, false, false));
    }

    @Test
    void closedOverrideIsReportedAndKeepsTheWindowClosed() {
        when(termRepository.findById(7L)).thenReturn(Optional.of(
                termWithWindow(NOW.minusDays(1), NOW.plusDays(1), TermEnrollmentOverride.CLOSED)));

        TermEnrollmentWindowView view = finder.findEnrollmentWindow(7L, NOW);

        assertThat(view.closedByOverride()).isTrue();
        assertThat(view.open()).isFalse();
    }

    @Test
    void invertedWindowIsReportedAsInvertedAndNotOpen() {
        when(termRepository.findById(7L)).thenReturn(Optional.of(termWithWindow(NOW.plusDays(10), NOW.minusDays(10), null)));

        assertThat(finder.findEnrollmentWindow(7L, NOW)).isEqualTo(new TermEnrollmentWindowView(7L, false, true, false));
    }

    @Test
    void missingTermFails() {
        when(termRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findEnrollmentWindow(7L, NOW)).isInstanceOf(TermNotFoundException.class);
    }
}
