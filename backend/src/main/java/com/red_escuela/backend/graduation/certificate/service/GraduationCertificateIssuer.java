package com.red_escuela.backend.graduation.certificate.service;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateResolver;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.source.GraduationSource;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class GraduationCertificateIssuer {

    private static final int CODE_ATTEMPTS = 3;

    private final Map<GraduationCertificateKind, GraduationSource> sources = new EnumMap<>(GraduationCertificateKind.class);
    private final GraduationCertificateRepository repository;
    private final CertificateTemplateResolver templateResolver;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GraduationCertificateIssuer(List<GraduationSource> graduationSources, GraduationCertificateRepository repository,
                                       CertificateTemplateResolver templateResolver, CertificatePdfRenderer renderer,
                                       CertificateFileStore fileStore, CertificateVerificationUrl verificationUrl,
                                       CertificateDigest digest, CurrentActorFinder currentActorFinder, Clock clock) {
        graduationSources.forEach(source -> sources.put(source.kind(), source));
        this.repository = repository;
        this.templateResolver = templateResolver;
        this.renderer = renderer;
        this.fileStore = fileStore;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public GraduationCertificateResponse issue(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationSource source = sources.get(kind);
        source.requireEligible(enrollmentId);
        if (alreadyIssued(kind, enrollmentId)) {
            throw new GraduationCertificateAlreadyIssuedException();
        }
        CertificateCode code = newUniqueCode();
        CertificateSnapshot snapshot = source.snapshotOf(enrollmentId);
        String url = verificationUrl.of(code);
        byte[] pdf = renderer.render(templateResolver.resolve(kind, source.titleIdOf(enrollmentId)), snapshot, code, url);
        String path = fileStore.save(code.value(), pdf);
        try {
            GraduationCertificate saved = repository.saveAndFlush(GraduationCertificate.issue(kind, enrollmentId,
                    source.studentIdOf(enrollmentId), code, snapshot, digest.sha256(pdf), path,
                    LocalDateTime.now(clock), currentActorFinder.currentPersonId()));
            return toResponse(saved, url);
        } catch (DataIntegrityViolationException e) {
            fileStore.deleteIfRolledBack(path);
            throw new GraduationCertificateAlreadyIssuedException();
        }
    }

    private boolean alreadyIssued(GraduationCertificateKind kind, Long enrollmentId) {
        return kind == GraduationCertificateKind.PROGRAM
                ? repository.existsByProgramEnrollmentIdAndStatus(enrollmentId, GraduationCertificateStatus.VALID)
                : repository.existsByCourseEnrollmentIdAndStatus(enrollmentId, GraduationCertificateStatus.VALID);
    }

    private CertificateCode newUniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            CertificateCode candidate = CertificateCode.generate(random);
            if (!repository.existsByCode(candidate.value())) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de certificado único");
    }

    private GraduationCertificateResponse toResponse(GraduationCertificate certificate, String url) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        return new GraduationCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getKind(),
                certificate.getStatus(), snapshot.holderFullName(), snapshot.titleName(), certificate.getGraduationDate(),
                certificate.getIssuedAt(), url);
    }
}
