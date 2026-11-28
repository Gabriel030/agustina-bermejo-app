package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.exception.TermHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermEndHousekeepingJobTest {

    private static final LocalDateTime TERM_END = LocalDateTime.of(2026, 12, 10, 23, 59);

    private final TermRepository termRepository = mock(TermRepository.class);
    private final TermDeactivation termDeactivation = mock(TermDeactivation.class);

    private TermEndHousekeepingJob jobAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new TermEndHousekeepingJob(termRepository, termDeactivation, TransactionOperations.withoutTransaction(), clock);
    }

    private Term endedTerm(Long id) {
        Term term = new Term();
        term.setId(id);
        term.setEndDate(TERM_END);
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        return term;
    }

    @Test
    void anExpiredOpenOverrideIsClearedAfterTheTermEnds() {
        Term term = endedTerm(1L);
        when(termRepository.findByStatusAndEndDateLessThan(any(), any())).thenReturn(List.of(term));
        when(termRepository.findById(1L)).thenReturn(Optional.of(term));

        assertThat(jobAt(TERM_END.plusHours(1)).clearExpiredOpenOverrides()).isEqualTo(1);
        assertThat(term.getEnrollmentOverride()).isNull();
        verify(termRepository).save(term);
    }

    @Test
    void aClosedOverrideIsLeftAlone() {
        Term term = endedTerm(1L);
        term.setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        when(termRepository.findByStatusAndEndDateLessThan(any(), any())).thenReturn(List.of(term));

        assertThat(jobAt(TERM_END.plusDays(3)).clearExpiredOpenOverrides()).isZero();
        verify(termRepository, never()).save(any());
    }

    @Test
    void aTermIsDeactivatedOnlyOncePastTheGraceWindow() {
        Term term = endedTerm(1L);
        when(termRepository.findByStatusAndEndDateLessThan(StatusType.ACTIVE, TERM_END.plusDays(2).minusDays(14)))
                .thenReturn(List.of());
        when(termRepository.findByStatusAndEndDateLessThan(StatusType.ACTIVE, TERM_END.plusDays(15).minusDays(14)))
                .thenReturn(List.of(term));

        assertThat(jobAt(TERM_END.plusDays(2)).deactivateTermsPastGrace()).isZero();
        assertThat(jobAt(TERM_END.plusDays(15)).deactivateTermsPastGrace()).isEqualTo(1);
        verify(termDeactivation).deactivate(1L);
    }

    @Test
    void aTermWithLiveEnrollmentsStaysActiveAndDoesNotStopTheOthers() {
        Term blocked = endedTerm(1L);
        Term free = endedTerm(2L);
        when(termRepository.findByStatusAndEndDateLessThan(any(), any())).thenReturn(List.of(blocked, free));
        doThrow(new TermHasActiveEnrollmentsException(1L)).when(termDeactivation).deactivate(1L);

        assertThat(jobAt(TERM_END.plusDays(20)).deactivateTermsPastGrace()).isEqualTo(1);
        verify(termDeactivation).deactivate(2L);
    }
}
