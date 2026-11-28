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
import com.red_escuela.backend.evaluation.events.ExamBoardWithoutTribunalEvent;
import com.red_escuela.backend.evaluation.exception.ExamBoardWithoutTribunalException;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
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
    static final int WITHOUT_TRIBUNAL_MARKER = 9000;

    private static final Logger log = LoggerFactory.getLogger(TermEndEscalationJob.class);

    private final TermEndClosureReview closureReview;
    private final CommissionClosureAutoCloser autoCloser;
    private final CommissionClosureReminderRepository reminderRepository;
    private final ExamBoardManager examBoardManager;
    private final ExamBoardRegistrationRepository registrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public TermEndEscalationJob(TermEndClosureReview closureReview, CommissionClosureAutoCloser autoCloser,
            CommissionClosureReminderRepository reminderRepository,
            ExamBoardManager examBoardManager, ExamBoardRegistrationRepository registrationRepository,
            ExamBoardRepository examBoardRepository, ExamBoardInstructorRepository examBoardInstructorRepository,
            ApplicationEventPublisher eventPublisher, TransactionOperations transactionOperations, Clock clock) {
        this.closureReview = closureReview;
        this.autoCloser = autoCloser;
        this.reminderRepository = reminderRepository;
        this.examBoardManager = examBoardManager;
        this.registrationRepository = registrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.eventPublisher = eventPublisher;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "TermEndEscalationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        closeGradedSections();
        escalateOverdueSections();
        closeExpiredExamBoards();
    }

    public int closeGradedSections() {
        LocalDate today = LocalDate.now(clock);
        int closed = 0;
        for (PendingSectionClosureResponse pending : closureReview.pendingSections(today)) {
            if (pending.graceOver() && pending.hasPublishedGrades() && closeGradedSafely(pending)) {
                closed++;
            }
        }
        log.info("Cierre automático de comisiones vencidas finalizado: fecha={}, cerradas={}", today, closed);
        return closed;
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

    private boolean closeGradedSafely(PendingSectionClosureResponse pending) {
        try {
            return autoCloser.closeIfGradesLoaded(SectionKind.valueOf(pending.sectionKind()), pending.sectionId());
        } catch (RuntimeException exception) {
            log.error("No se pudo cerrar automáticamente la comisión vencida: kind={}, sectionId={}", pending.sectionKind(), pending.sectionId(), exception);
            return false;
        }
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
                if (!examBoardInstructorRepository.existsByExamBoardId(boardId)) {
                    announceWithoutTribunal(current);
                    return false;
                }
                long absentBefore = registrationRepository.countByExamBoardIdAndStatus(boardId, ExamBoardRegistrationStatus.ABSENT);
                ExamBoard closed = examBoardManager.close(boardId);
                long absentNow = registrationRepository.countByExamBoardIdAndStatus(boardId, ExamBoardRegistrationStatus.ABSENT) - absentBefore;
                String subjectName = closed.getSubject() != null ? closed.getSubject().getName() : closed.getCourseSubject().getName();
                eventPublisher.publishEvent(ExamBoardAutoClosedEvent.of(boardId, subjectName, absentNow));
                return true;
            }));
        } catch (ExamBoardWithoutTribunalException exception) {
            log.warn("La mesa vencida sigue sin tribunal y no se puede cerrar: examBoardId={}", boardId);
            return false;
        } catch (RuntimeException exception) {
            log.error("No se pudo cerrar la mesa vencida: examBoardId={}", boardId, exception);
            return false;
        }
    }

    private void announceWithoutTribunal(ExamBoard board) {
        SectionKind kind = board.getSubject() != null ? SectionKind.PROGRAM : SectionKind.COURSE;
        int inserted = reminderRepository.insertIfAbsent(kind, board.getId(), WITHOUT_TRIBUNAL_MARKER, LocalDateTime.now(clock));
        if (inserted == 0) {
            return;
        }
        String subjectName = board.getSubject() != null ? board.getSubject().getName() : board.getCourseSubject().getName();
        log.warn("La mesa vencida no se cierra sola porque no tiene tribunal: examBoardId={}", board.getId());
        eventPublisher.publishEvent(ExamBoardWithoutTribunalEvent.of(board.getId(), subjectName));
    }
}
