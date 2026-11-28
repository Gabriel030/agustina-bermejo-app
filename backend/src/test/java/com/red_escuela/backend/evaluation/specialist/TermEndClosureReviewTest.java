package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.EndedSectionFinder;
import com.red_escuela.backend.academicstructure.api.EndedSectionRef;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.dto.PendingClosureState;
import com.red_escuela.backend.evaluation.dto.PendingSectionClosureResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardAuditRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TermEndClosureReviewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 30);

    private final EndedSectionFinder endedSectionFinder = mock(EndedSectionFinder.class);
    private final CommissionClosureRepository closureRepository = mock(CommissionClosureRepository.class);
    private final SectionGradeItemPublicationRepository publicationRepository = mock(SectionGradeItemPublicationRepository.class);
    private final ClosureNotificationTargetsFinder targetsFinder = mock(ClosureNotificationTargetsFinder.class);
    private final TermEndClosureReview review = new TermEndClosureReview(endedSectionFinder, closureRepository, publicationRepository,
            targetsFinder, mock(ExamBoardRepository.class), mock(ExamBoardRegistrationRepository.class),
            mock(ExamBoardAuditRepository.class), mock(ExamBoardInstructorRepository.class));

    private void endedSection(Long id, LocalDate termEnd) {
        when(endedSectionFinder.findActiveSectionsEndedBetween(any(), any())).thenReturn(List.of(new EndedSectionRef(SectionKind.PROGRAM, id, termEnd)));
        when(targetsFinder.findSectionLabel(SectionKind.PROGRAM, id)).thenReturn("Teorías · A");
    }

    private CommissionClosure closureWith(ClosureStatus status) {
        return CommissionClosure.builder().status(status).build();
    }

    @Test
    void aSectionWithoutAnyClosureAndWithoutGradesIsListedAsNotStarted() {
        endedSection(9L, TODAY.minusDays(5));
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.empty());
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(List.of());

        List<PendingSectionClosureResponse> pending = review.pendingSections(TODAY);

        assertThat(pending).singleElement().satisfies(item -> {
            assertThat(item.closureState()).isEqualTo(PendingClosureState.NOT_STARTED);
            assertThat(item.hasPublishedGrades()).isFalse();
            assertThat(item.daysAfterTermEnd()).isEqualTo(5);
            assertThat(item.graceOver()).isFalse();
        });
    }

    @Test
    void aClosedSectionIsNotListed() {
        endedSection(9L, TODAY.minusDays(30));
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.of(closureWith(ClosureStatus.CLOSED)));

        assertThat(review.pendingSections(TODAY)).isEmpty();
    }

    @Test
    void aTeacherConfirmedSectionPastTheGraceStaysListedAsOverdue() {
        endedSection(9L, TODAY.minusDays(15));
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L))
                .thenReturn(Optional.of(closureWith(ClosureStatus.TEACHER_CONFIRMED)));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(List.of(new SectionGradeItemPublication()));

        assertThat(review.pendingSections(TODAY)).singleElement().satisfies(item -> {
            assertThat(item.graceOver()).isTrue();
            assertThat(item.closureState()).isEqualTo(PendingClosureState.TEACHER_CONFIRMED);
            assertThat(item.hasPublishedGrades()).isTrue();
        });
    }

    @Test
    void theGraceWindowIncludesTheFourteenthDayAndEndsOnTheFifteenth() {
        endedSection(9L, TODAY.minusDays(14));
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.empty());

        assertThat(review.pendingSections(TODAY)).singleElement().extracting(PendingSectionClosureResponse::graceOver).isEqualTo(false);
        assertThat(review.pendingSections(TODAY.plusDays(1))).singleElement().extracting(PendingSectionClosureResponse::graceOver).isEqualTo(true);
    }
}
