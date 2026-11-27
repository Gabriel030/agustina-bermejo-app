package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardAudit;
import com.red_escuela.backend.evaluation.entity.ExamBoardAuditAction;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardReopenReasonException;
import com.red_escuela.backend.evaluation.repository.ExamBoardAuditRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ExamBoardReopener {

    static final int MINIMUM_REASON_LENGTH = 10;

    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardAuditRepository auditRepository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public ExamBoardReopener(ExamBoardRepository examBoardRepository, ExamBoardAuditRepository auditRepository,
                             CurrentActorFinder currentActorFinder, Clock clock) {
        this.examBoardRepository = examBoardRepository;
        this.auditRepository = auditRepository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public ExamBoardResponse reopen(Long examBoardId, String reason) {
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() < MINIMUM_REASON_LENGTH) {
            throw new InvalidExamBoardReopenReasonException(MINIMUM_REASON_LENGTH);
        }
        ExamBoard examBoard = examBoardRepository.findByIdForUpdate(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        if (examBoard.getStatus() != ExamBoardStatus.CLOSED) {
            throw new ExamBoardNotClosedException(examBoardId);
        }
        examBoard.reopen();
        ExamBoard saved = examBoardRepository.save(examBoard);
        auditRepository.save(ExamBoardAudit.builder()
                .examBoardId(examBoardId)
                .action(ExamBoardAuditAction.REOPENED)
                .actorPersonId(currentActorFinder.currentPersonId())
                .reason(trimmedReason)
                .occurredAt(LocalDateTime.now(clock))
                .build());
        return ExamBoardResponse.from(saved);
    }
}
