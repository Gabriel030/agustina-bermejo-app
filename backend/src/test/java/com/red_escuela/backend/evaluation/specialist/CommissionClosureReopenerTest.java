package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionClosureReopenerTest {

    private final CommissionClosureRepository closureRepository = mock(CommissionClosureRepository.class);
    private final ClosureSectionEnrollmentLocker locker = mock(ClosureSectionEnrollmentLocker.class);
    private final GraduationRequestTriggerPort graduation = mock(GraduationRequestTriggerPort.class);
    private final CommissionClosureReopener reopener = new CommissionClosureReopener(
            mock(SectionFinder.class), locker, graduation, closureRepository, mock(CommissionClosureAuditRepository.class),
            mock(CurrentActorFinder.class), mock(ApplicationEventPublisher.class),
            Clock.fixed(Instant.parse("2026-12-20T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void reopeningFlagsTheGraduationRequestOfEveryProgramEnrollmentAsOutdated() {
        CommissionClosure closure = CommissionClosure.builder().id(9L).status(ClosureStatus.CLOSED).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 5L)).thenReturn(Optional.of(closure));
        when(closureRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(closure));
        when(locker.programEnrollmentIdsOf(5L)).thenReturn(List.of(71L, 72L));

        reopener.reopenClosure(ClosureSectionKind.PROGRAM, 5L, "Se corrigió una nota publicada");

        verify(graduation).flagGraduationRequestOutdated(71L);
        verify(graduation).flagGraduationRequestOutdated(72L);
    }
}
