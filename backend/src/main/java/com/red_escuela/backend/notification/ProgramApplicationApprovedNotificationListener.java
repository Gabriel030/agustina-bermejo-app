package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.events.AdmissionEnrollmentStalledEvent;
import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProgramApplicationApprovedNotificationListener {
    static final String APPROVED_HANDLER = "ProgramApplicationApprovedNotificationListener.notifyStudentOfApprovedApplication";
    static final String STALLED_HANDLER = "ProgramApplicationApprovedNotificationListener.notifySecretariatOfStalledEnrollment";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public ProgramApplicationApprovedNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyStudentOfApprovedApplication(ProgramApplicationApprovedEvent event) {
        effectGuard.runEffectOnce(event, APPROVED_HANDLER, () -> eventPublisher.publishEvent(
                new ProgramApplicationApprovedNotification(event.applicationId(), event.studentPersonId(), event.programName())));
    }

    @ApplicationModuleListener
    public void notifySecretariatOfStalledEnrollment(AdmissionEnrollmentStalledEvent event) {
        effectGuard.runEffectOnce(event, STALLED_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PROGRAM_ENROLLMENT_STALLED,
                "No pudimos inscribir a " + event.studentFullName() + " en " + event.programName()
                        + " después de confirmar el pago de la matrícula de ingreso (solicitud " + event.applicationId()
                        + "). Revisá la inscripción manualmente.",
                List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "ProgramEnrollmentApplication", event.applicationId())));
    }
}
