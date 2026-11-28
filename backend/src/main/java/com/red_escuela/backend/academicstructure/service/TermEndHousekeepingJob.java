package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.TermEndGracePolicy;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TermEndHousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(TermEndHousekeepingJob.class);

    private final TermRepository termRepository;
    private final TermDeactivation termDeactivation;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public TermEndHousekeepingJob(TermRepository termRepository, TermDeactivation termDeactivation,
            TransactionOperations transactionOperations, Clock clock) {
        this.termRepository = termRepository;
        this.termDeactivation = termDeactivation;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 9 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "TermEndHousekeepingJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        clearExpiredOpenOverrides();
        deactivateTermsPastGrace();
    }

    public int clearExpiredOpenOverrides() {
        LocalDateTime now = LocalDateTime.now(clock);
        int cleared = 0;
        for (Term term : termRepository.findByStatusAndEndDateLessThan(StatusType.ACTIVE, now)) {
            if (term.hasOpenOverrideExpiredAt(now) && runSafely(term.getId(), () -> clearOverride(term.getId()))) {
                cleared++;
            }
        }
        log.info("Overrides de inscripción vencidos limpiados: {}", cleared);
        return cleared;
    }

    public int deactivateTermsPastGrace() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime graceLimit = now.minusDays(TermEndGracePolicy.GRACE_DAYS);
        int deactivated = 0;
        for (Term term : termRepository.findByStatusAndEndDateLessThan(StatusType.ACTIVE, graceLimit)) {
            if (runSafely(term.getId(), () -> termDeactivation.deactivate(term.getId()))) {
                deactivated++;
            }
        }
        log.info("Términos desactivados al vencer la gracia: {}", deactivated);
        return deactivated;
    }

    private void clearOverride(Long termId) {
        Term term = termRepository.findById(termId).orElseThrow();
        term.setEnrollmentOverride(null);
        termRepository.save(term);
    }

    private boolean runSafely(Long termId, Runnable action) {
        try {
            transactionOperations.executeWithoutResult(status -> action.run());
            return true;
        } catch (TermHasActiveEnrollmentsException exception) {
            log.info("El término {} sigue con inscripciones vivas: no se desactiva", termId);
            return false;
        } catch (RuntimeException exception) {
            log.error("No se pudo procesar el fin de término: termId={}", termId, exception);
            return false;
        }
    }
}
