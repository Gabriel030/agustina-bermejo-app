package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.shared.domain.TermEndGracePolicy;
import com.red_escuela.backend.evaluation.dto.PendingSectionClosureResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.events.ExamBoardAutoClosedEvent;
import com.red_escuela.backend.evaluation.events.TermEndClosureOverdueEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class TermEndEscalationJob {

    static final int ESCALATION_THRESHOLD = TermEndGracePolicy.GRACE_DAYS + 1;

    private static final Logger log = LoggerFactory.getLogger(TermEndEscalationJob.class);

    private final TermEndClosureReview closureReview;
    private final CommissionClosureReminderRepository reminderRepository;
    private final ExamBoardManager examBoardManager;
    private final ExamBoardRegistrationRepository registrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public TermEndEscalationJob(TermEndClosureReview closureReview, CommissionClosureReminderRepository reminderRepository,
            ExamBoardManager examBoardManager, ExamBoardRegistrationRepository registrationRepository,
            ExamBoardRepository examBoardRepository, ApplicationEventPublisher eventPublisher, TransactionOperations transactionOperations, Clock clock) {
        this.closureReview = closureReview;
        this.reminderRepository = reminderRepository;
        this.examBoardManager = examBoardManager;
        this.registrationRepository = registrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.eventPublisher = eventPublisher;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "TermEndEscalationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        escalateOverdueSections();
        closeExpiredExamBoards();
    }

    public int escalateOverdueSections() {
        LocalDate today = LocalDate.now(clock);
        int escalated = 0;
        for (PendingSectionClosureResponse pending : closureReview.pendingSections(today)) {
            if (pending.graceOver() && escalateSafely(pending)) {
                escalated++;
            }
        }
        log.info("Escalada de cierres vencidos finalizada: fecha={}, escaladas={}", today, escalated);
        return escalated;
    }

    public int closeExpiredExamBoards() {
        LocalDate today = LocalDate.now(clock);
        int closed = 0;
        for (ExamBoard board : closureReview.expiredScheduledBoards(today)) {
            if (TermEndGracePolicy.isGraceOver(closureReview.graceReference(board), today) && closeSafely(board)) {
                closed++;
            }
        }
        log.info("Cierre de mesas vencidas finalizado: fecha={}, cerradas={}", today, closed);
        return closed;
    }

    private boolean escalateSafely(PendingSectionClosureResponse pending) {
        try {
            return Boolean.TRUE.equals(transactionOperations.execute(status -> escalate(pending)));
        } catch (RuntimeException exception) {
            log.error("No se pudo escalar el cierre vencido: kind={}, sectionId={}", pending.sectionKind(), pending.sectionId(), exception);
            return false;
        }
    }

    private boolean escalate(PendingSectionClosureResponse pending) {
        int inserted = reminderRepository.insertIfAbsent(SectionKind.valueOf(pending.sectionKind()), pending.sectionId(),
                ESCALATION_THRESHOLD, LocalDateTime.now(clock));
        if (inserted == 0) {
            return false;
        }
        eventPublisher.publishEvent(TermEndClosureOverdueEvent.of(pending.sectionKind(), pending.sectionId(),
                pending.sectionLabel(), pending.daysAfterTermEnd()));
        return true;
    }

    private boolean closeSafely(ExamBoard board) {
        Long boardId = board.getId();
        try {
            return Boolean.TRUE.equals(transactionOperations.execute(status -> {
                ExamBoard current = examBoardRepository.findByIdForUpdate(boardId).orElse(null);
                if (current == null || current.getStatus() != ExamBoardStatus.SCHEDULED) {
                    return false;
                }
                long absentBefore = registrationRepository.countByExamBoardIdAndStatus(boardId, ExamBoardRegistrationStatus.ABSENT);
                ExamBoard closed = examBoardManager.close(boardId);
                long absentNow = registrationRepository.countByExamBoardIdAndStatus(boardId, ExamBoardRegistrationStatus.ABSENT) - absentBefore;
                String subjectName = closed.getSubject() != null ? closed.getSubject().getName() : closed.getCourseSubject().getName();
                eventPublisher.publishEvent(ExamBoardAutoClosedEvent.of(boardId, subjectName, absentNow));
                return true;
            }));
        } catch (RuntimeException exception) {
            log.error("No se pudo cerrar la mesa vencida: examBoardId={}", boardId, exception);
            return false;
        }
    }
}
