package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.GraduationCertificateIssuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateReissuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.GraduationCertificateNotification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class GraduationCertificateNotificationListener {
    static final String NOTIFY_ISSUED_HANDLER = "GraduationCertificateNotificationListener.notifyIssued";
    static final String NOTIFY_REVOKED_HANDLER = "GraduationCertificateNotificationListener.notifyRevoked";
    static final String NOTIFY_REISSUED_HANDLER = "GraduationCertificateNotificationListener.notifyReissued";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public GraduationCertificateNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
                                                     EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyIssued(GraduationCertificateIssuedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ISSUED_HANDLER, () -> eventPublisher.publishEvent(
                notification(NotificationType.GRADUATION_CERTIFICATE_ISSUED,
                        "Tu certificado de egreso ya está disponible: " + event.titleName(), event.studentPersonId(), event.certificateId())));
    }

    @ApplicationModuleListener
    public void notifyRevoked(GraduationCertificateRevokedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REVOKED_HANDLER, () -> eventPublisher.publishEvent(
                notification(NotificationType.GRADUATION_CERTIFICATE_REVOKED,
                        "Tu certificado de egreso fue revocado: " + event.titleName(), event.studentPersonId(), event.certificateId())));
    }

    @ApplicationModuleListener
    public void notifyReissued(GraduationCertificateReissuedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REISSUED_HANDLER, () -> eventPublisher.publishEvent(
                notification(NotificationType.GRADUATION_CERTIFICATE_REISSUED,
                        "Tu certificado de egreso fue reemitido: " + event.titleName(), event.studentPersonId(), event.certificateId())));
    }

    private GraduationCertificateNotification notification(NotificationType type, String message, Long personId, Long certificateId) {
        return new GraduationCertificateNotification(type, message, entityManager.getReference(Person.class, personId), certificateId);
    }
}
