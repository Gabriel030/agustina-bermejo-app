package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.enrollment.events.AdmissionEnrollmentStalledEvent;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdmissionEnrollmentStalledNotifier {
    private static final Logger log = LoggerFactory.getLogger(AdmissionEnrollmentStalledNotifier.class);
    static final Duration STALLED_AFTER = Duration.ofHours(1);

    private static final String FIND_STALLED_APPLICATIONS = """
            select a.id from program_enrollment_application a
            join event_publication p on p.event_type = ? and p.listener_id like ? and p.completion_date is null
                and (p.serialized_event::jsonb ->> 'invoiceId')::bigint = a.admission_invoice_id
            where a.status = 'APPROVED' and a.enrollment_failure_notified_at is null and p.publication_date < ?
                and not exists (select 1 from program_enrollment e where e.admission_application_id = a.id)
            order by a.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdmissionEnrollmentStalledNotifier(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                                              ProgramEnrollmentApplicationRepository applicationRepository,
                                              ApplicationEventPublisher eventPublisher, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public int notifySecretariatOfStalledEnrollments() {
        OffsetDateTime threshold = OffsetDateTime.ofInstant(clock.instant().minus(STALLED_AFTER), clock.getZone());
        List<Long> applicationIds = jdbcTemplate.queryForList(FIND_STALLED_APPLICATIONS, Long.class,
                AdmissionInvoicePaidEvent.class.getName(), "%" + ProgramEnrollmentAdmissionPaymentListener.class.getName() + "%", threshold);
        int notified = 0;
        for (Long applicationId : applicationIds) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> notifyOnce(applicationId)))) {
                    notified++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo avisar a Secretaría de la inscripción trabada de la solicitud {}", applicationId, e);
            }
        }
        return notified;
    }

    private boolean notifyOnce(Long applicationId) {
        return applicationRepository.findLockedById(applicationId)
                .filter(application -> application.getEnrollmentFailureNotifiedAt() == null)
                .map(application -> {
                    application.markEnrollmentFailureNotified();
                    var person = application.getStudent().getPerson();
                    eventPublisher.publishEvent(AdmissionEnrollmentStalledEvent.stalledFor(application.getId(),
                            person.getName() + " " + person.getLastname(), application.getProgram().getName()));
                    log.warn("Inscripción trabada por más de {} tras el pago de la matrícula de ingreso: solicitud={}", STALLED_AFTER, application.getId());
                    return true;
                }).orElse(false);
    }
}
