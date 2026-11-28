package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.EndedSectionFinder;
import com.red_escuela.backend.academicstructure.api.EndedSectionRef;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.shared.domain.TermEndGracePolicy;
import com.red_escuela.backend.evaluation.dto.PendingExamBoardClosureResponse;
import com.red_escuela.backend.evaluation.dto.PendingClosureState;
import com.red_escuela.backend.evaluation.dto.PendingSectionClosureResponse;
import com.red_escuela.backend.evaluation.dto.PendingTermClosuresResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
@Transactional(readOnly = true)
public class TermEndClosureReview {

    private static final int LOOKBACK_DAYS = 120;

    private final EndedSectionFinder endedSectionFinder;
    private final CommissionClosureRepository closureRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final ClosureNotificationTargetsFinder targetsFinder;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardRegistrationRepository registrationRepository;

    public TermEndClosureReview(EndedSectionFinder endedSectionFinder, CommissionClosureRepository closureRepository,
            SectionGradeItemPublicationRepository publicationRepository, ClosureNotificationTargetsFinder targetsFinder,
            ExamBoardRepository examBoardRepository, ExamBoardRegistrationRepository registrationRepository) {
        this.endedSectionFinder = endedSectionFinder;
        this.closureRepository = closureRepository;
        this.publicationRepository = publicationRepository;
        this.targetsFinder = targetsFinder;
        this.examBoardRepository = examBoardRepository;
        this.registrationRepository = registrationRepository;
    }

    public PendingTermClosuresResponse review(LocalDate today) {
        return new PendingTermClosuresResponse(TermEndGracePolicy.GRACE_DAYS, pendingSections(today), pendingExamBoards(today));
    }

    public List<PendingSectionClosureResponse> pendingSections(LocalDate today) {
        return endedSectionFinder.findActiveSectionsEndedBetween(today.minusDays(LOOKBACK_DAYS), today.minusDays(1)).stream()
                .map(ref -> toPendingSection(ref, today))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ExamBoard> expiredScheduledBoards(LocalDate today) {
        return examBoardRepository.findByStatusAndExamDateLessThanOrderByExamDate(ExamBoardStatus.SCHEDULED, today.atStartOfDay());
    }

    public List<PendingExamBoardClosureResponse> pendingExamBoards(LocalDate today) {
        return expiredScheduledBoards(today).stream().map(board -> toPendingBoard(board, today)).toList();
    }

    private PendingSectionClosureResponse toPendingSection(EndedSectionRef ref, LocalDate today) {
        ClosureStatus status = closureRepository.findBySectionKindAndSectionId(ref.kind(), ref.sectionId())
                .map(closure -> closure.getStatus()).orElse(null);
        if (status == ClosureStatus.CLOSED) {
            return null;
        }
        boolean published = !publicationRepository.findBySectionKindAndSectionId(ref.kind(), ref.sectionId()).isEmpty();
        return new PendingSectionClosureResponse(ref.kind().name(), ref.sectionId(),
                targetsFinder.findSectionLabel(ref.kind(), ref.sectionId()), ref.termEnd(),
                TermEndGracePolicy.daysAfter(ref.termEnd(), today), TermEndGracePolicy.isGraceOver(ref.termEnd(), today),
                PendingClosureState.from(status), published);
    }

    private PendingExamBoardClosureResponse toPendingBoard(ExamBoard board, LocalDate today) {
        LocalDate examDay = board.getExamDate().toLocalDate();
        String subjectName = board.getSubject() != null ? board.getSubject().getName() : board.getCourseSubject().getName();
        return new PendingExamBoardClosureResponse(board.getId(), subjectName, board.getExamDate(),
                ChronoUnit.DAYS.between(examDay, today), TermEndGracePolicy.isGraceOver(examDay, today),
                registrationRepository.countByExamBoardIdAndStatus(board.getId(), ExamBoardRegistrationStatus.REGISTERED));
    }
}
