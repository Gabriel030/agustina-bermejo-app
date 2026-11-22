package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.events.GraduationCertificateIssuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateReissuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateRevokedEvent;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationCertificateEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final StudentFinder studentFinder;

    public GraduationCertificateEventPublisher(ApplicationEventPublisher eventPublisher, StudentFinder studentFinder) {
        this.eventPublisher = eventPublisher;
        this.studentFinder = studentFinder;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issued(GraduationCertificate certificate) {
        eventPublisher.publishEvent(GraduationCertificateIssuedEvent.of(certificate.getId(), personOf(certificate),
                certificate.getSnapshot().titleName()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoked(GraduationCertificate certificate) {
        eventPublisher.publishEvent(GraduationCertificateRevokedEvent.of(certificate.getId(), personOf(certificate),
                certificate.getSnapshot().titleName()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reissued(GraduationCertificate replacement, Long replacedCertificateId) {
        eventPublisher.publishEvent(GraduationCertificateReissuedEvent.of(replacement.getId(), replacedCertificateId,
                personOf(replacement), replacement.getSnapshot().titleName()));
    }

    private Long personOf(GraduationCertificate certificate) {
        return studentFinder.requireById(certificate.getStudentId()).getPerson().getId();
    }
}
