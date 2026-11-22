package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateRevokedException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OwnGraduationCertificateDownloader {

    private final GraduationCertificateRepository repository;
    private final GraduationCertificatePdfProvider pdfProvider;
    private final CurrentActorFinder currentActorFinder;
    private final AuditTrail auditTrail;

    public OwnGraduationCertificateDownloader(GraduationCertificateRepository repository, GraduationCertificatePdfProvider pdfProvider,
                                              CurrentActorFinder currentActorFinder, AuditTrail auditTrail) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
        this.currentActorFinder = currentActorFinder;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public Optional<GraduationCertificatePdf> download(Long id) {
        Long studentId = currentActorFinder.currentStudentId();
        GraduationCertificate certificate = repository.findById(id)
                .filter(found -> found.getStudentId().equals(studentId))
                .orElseThrow(() -> new GraduationCertificateNotFoundException(id));
        if (!certificate.isRevocable()) {
            throw new GraduationCertificateRevokedException();
        }
        Optional<GraduationCertificatePdf> pdf = pdfProvider.verifiedPdfOf(certificate);
        AuditAction action = pdf.isPresent() ? AuditAction.GRADUATION_CERTIFICATE_DOWNLOADED : AuditAction.GRADUATION_CERTIFICATE_PDF_MISMATCH;
        auditTrail.record(new AuditRecord(action, AuditEntityType.GRADUATION_CERTIFICATE, certificate.getId(), null, null, null));
        return pdf;
    }
}
