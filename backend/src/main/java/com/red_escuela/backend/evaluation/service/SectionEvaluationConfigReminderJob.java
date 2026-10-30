package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class SectionEvaluationConfigReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SectionEvaluationConfigReminderJob.class);
    private static final List<Integer> REMINDER_DAYS_BEFORE = List.of(10, 5);

    private final SectionEvaluationConfigRepository configRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SectionEvaluationConfigReminderJob(
            SectionEvaluationConfigRepository configRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository,
            ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "SectionEvaluationConfigReminderJob", lockAtMostFor = "PT30M")
    @Transactional
    public void sendReminders() {
        LocalDate today = ApplicationTime.today();
        log.info("Recordatorios de configuración de evaluación iniciados: fecha={}", today);
        long start = System.currentTimeMillis();
        int sent = 0;
        List<SectionEvaluationConfig> pending = configRepository.findByStatusIn(List.of(
                SectionEvaluationConfigStatus.DRAFT,
                SectionEvaluationConfigStatus.PENDING_APPROVAL,
                SectionEvaluationConfigStatus.REJECTED));

        for (SectionEvaluationConfig config : pending) {
            Term term = config.getSubjectSection().getTerm();
            if (term.getEnrollmentStart() == null) {
                continue;
            }
            long daysToEnrollmentStart = ChronoUnit.DAYS.between(today, term.getEnrollmentStart().toLocalDate());
            if (REMINDER_DAYS_BEFORE.contains((int) daysToEnrollmentStart)) {
                Optional<Person> lead = leadInstructorPerson(config);
                if (lead.isPresent()) {
                    eventPublisher.publishEvent(
                            SectionEvaluationConfigReminderEvent.reminderFor(config, lead.get(), (int) daysToEnrollmentStart));
                    sent++;
                }
            }
        }
        log.info("Recordatorios de configuración de evaluación finalizados: pendientes={}, enviados={}, duracion={}ms",
                pending.size(), sent, System.currentTimeMillis() - start);
    }

    private Optional<Person> leadInstructorPerson(SectionEvaluationConfig config) {
        return sectionInstructorRepository.findBySectionId(config.getSubjectSection().getId()).stream()
                .filter(instructor -> instructor.getRole() == InstructorRole.LEAD)
                .findFirst()
                .map(instructor -> instructor.getInstructor().getPerson());
    }
}
