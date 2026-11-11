package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.events.ClosureConfirmationReminderEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.PublishedSectionRef;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ClosureConfirmationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ClosureConfirmationReminderJob.class);
    private static final Set<ClosureStatus> CONFIRMED_STATUSES = Set.of(ClosureStatus.TEACHER_CONFIRMED, ClosureStatus.CLOSED);
    private static final int FIRST_REMINDER_DAYS = 3;
    private static final int SECOND_REMINDER_DAYS = 7;
    private static final int LAST_REMINDABLE_DAY = 14;

    private final SectionGradeItemPublicationRepository publicationRepository;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureReminderRepository reminderRepository;
    private final SectionTermEndFinder sectionTermEndFinder;
    private final ClosureNotificationTargetsFinder targetsFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClosureConfirmationReminderJob(SectionGradeItemPublicationRepository publicationRepository,
            CommissionClosureRepository closureRepository, CommissionClosureReminderRepository reminderRepository,
            SectionTermEndFinder sectionTermEndFinder, ClosureNotificationTargetsFinder targetsFinder,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.publicationRepository = publicationRepository;
        this.closureRepository = closureRepository;
        this.reminderRepository = reminderRepository;
        this.sectionTermEndFinder = sectionTermEndFinder;
        this.targetsFinder = targetsFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ClosureConfirmationReminderJob", lockAtMostFor = "PT30M")
    @Transactional
    public void sendReminders() {
        remindDueSections();
    }

    @Transactional
    public int remindDueSections() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        List<PublishedSectionRef> sections = publicationRepository.findDistinctPublishedSections();
        int sent = 0;
        for (PublishedSectionRef section : sections) {
            sent += remindIfDue(section, today, now) ? 1 : 0;
        }
        log.info("Recordatorios de confirmación de cierre finalizados: fecha={}, secciones={}, enviados={}", today, sections.size(), sent);
        return sent;
    }

    private boolean remindIfDue(PublishedSectionRef section, LocalDate today, LocalDateTime now) {
        SectionKind kind = section.sectionKind();
        Long sectionId = section.sectionId();
        boolean confirmed = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .map(closure -> CONFIRMED_STATUSES.contains(closure.getStatus())).orElse(false);
        LocalDate termEnd = confirmed ? null : sectionTermEndFinder.termEndDate(kind, sectionId);
        Optional<Integer> threshold = termEnd == null ? Optional.empty() : thresholdFor(ChronoUnit.DAYS.between(termEnd, today));
        if (threshold.isEmpty()) {
            return false;
        }
        List<Long> instructors = targetsFinder.findInstructorPersonIds(kind, sectionId);
        if (instructors.isEmpty() || reminderRepository.insertIfAbsent(kind, sectionId, threshold.get(), now) == 0) {
            return false;
        }
        eventPublisher.publishEvent(ClosureConfirmationReminderEvent.of(kind.name(), sectionId,
                targetsFinder.findSectionLabel(kind, sectionId), instructors, threshold.get()));
        return true;
    }

    private Optional<Integer> thresholdFor(long daysAfterTermEnd) {
        if (daysAfterTermEnd >= SECOND_REMINDER_DAYS && daysAfterTermEnd <= LAST_REMINDABLE_DAY) {
            return Optional.of(SECOND_REMINDER_DAYS);
        }
        if (daysAfterTermEnd >= FIRST_REMINDER_DAYS && daysAfterTermEnd < SECOND_REMINDER_DAYS) {
            return Optional.of(FIRST_REMINDER_DAYS);
        }
        return Optional.empty();
    }
}
