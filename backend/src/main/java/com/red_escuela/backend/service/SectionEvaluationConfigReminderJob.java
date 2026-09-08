package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.SectionEvaluationConfig;
import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.notification.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
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

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional(readOnly = true)
    public void sendReminders() {
        LocalDate today = LocalDate.now();
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
                leadInstructorPerson(config).ifPresent(person -> eventPublisher.publishEvent(
                        new SectionEvaluationConfigReminderEvent(config, person, (int) daysToEnrollmentStart)));
            }
        }
    }

    private Optional<Person> leadInstructorPerson(SectionEvaluationConfig config) {
        return sectionInstructorRepository.findBySectionId(config.getSubjectSection().getId()).stream()
                .filter(instructor -> instructor.getRole() == InstructorRole.LEAD)
                .findFirst()
                .map(instructor -> instructor.getInstructor().getPerson());
    }
}
