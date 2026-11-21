package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureUpload;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatorySignature;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatorySignatureInvalidException;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatorySignatureRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageInvalidException;
import com.red_escuela.backend.graduation.certificate.template.specialist.TemplatePngInspector;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateSignatorySignatureWriter {

    private final CertificateSignatoryFinder finder;
    private final CertificateSignatorySignatureRepository signatureRepository;
    private final CertificateSignatoryReader reader;
    private final TemplatePngInspector pngInspector;
    private final CertificateDigest digest;
    private final CurrentActorFinder currentActorFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CertificateSignatorySignatureWriter(CertificateSignatoryFinder finder, CertificateSignatorySignatureRepository signatureRepository,
                                               CertificateSignatoryReader reader, TemplatePngInspector pngInspector, CertificateDigest digest,
                                               CurrentActorFinder currentActorFinder, AuditTrail auditTrail, Clock clock) {
        this.finder = finder;
        this.signatureRepository = signatureRepository;
        this.reader = reader;
        this.pngInspector = pngInspector;
        this.digest = digest;
        this.currentActorFinder = currentActorFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public CertificateSignatoryDetailResponse upload(Long id, SignatorySignatureUpload upload) {
        CertificateSignatory signatory = finder.find(id);
        TemplatePngInspector.PngFacts facts;
        try {
            facts = pngInspector.inspect(upload.content());
        } catch (TemplateImageInvalidException e) {
            throw new SignatorySignatureInvalidException();
        }
        signatureRepository.findBySignatoryId(id).ifPresent(existing -> {
            signatureRepository.delete(existing);
            signatureRepository.flush();
        });
        LocalDateTime now = LocalDateTime.now(clock);
        signatureRepository.saveAndFlush(CertificateSignatorySignature.builder()
                .signatoryId(id)
                .contentType("image/png")
                .sizeBytes(upload.content().length)
                .widthPixels(facts.widthPixels())
                .heightPixels(facts.heightPixels())
                .sha256(digest.sha256(upload.content()))
                .content(upload.content())
                .uploadedAt(now)
                .uploadedById(currentActorFinder.currentPersonId())
                .build());
        signatory.touch(now);
        auditTrail.record(new AuditRecord(AuditAction.CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED, AuditEntityType.CERTIFICATE_SIGNATORY, id,
                null, null, null));
        return reader.detailOf(signatory);
    }

    @Transactional
    public CertificateSignatoryDetailResponse delete(Long id) {
        CertificateSignatory signatory = finder.find(id);
        CertificateSignatorySignature existing = signatureRepository.findBySignatoryId(id).orElse(null);
        if (existing != null) {
            signatureRepository.delete(existing);
            signatureRepository.flush();
            signatory.touch(LocalDateTime.now(clock));
            auditTrail.record(new AuditRecord(AuditAction.CERTIFICATE_SIGNATORY_SIGNATURE_DELETED, AuditEntityType.CERTIFICATE_SIGNATORY, id,
                    null, null, null));
        }
        return reader.detailOf(signatory);
    }
}
