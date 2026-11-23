package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.source.GraduationSource;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
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
    private final BuiltInCertificateTemplate builtInTemplate;
    private final CertificateTemplateRenderSource templateSource;
    private final CertificateRegistryNumberAssigner registryNumberAssigner;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;
    private final GraduationCertificateEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public GraduationCertificateIssuer(List<GraduationSource> graduationSources, GraduationCertificateRepository repository,
                                       BuiltInCertificateTemplate builtInTemplate, CertificateTemplateRenderSource templateSource,
                                       CertificateRegistryNumberAssigner registryNumberAssigner, CertificatePdfRenderer renderer,
                                       CertificateFileStore fileStore, CertificateVerificationUrl verificationUrl,
                                       CertificateDigest digest, CurrentActorFinder currentActorFinder, Clock clock,
                                       GraduationCertificateEventPublisher eventPublisher) {
        graduationSources.forEach(source -> sources.put(source.kind(), source));
        this.repository = repository;
        this.builtInTemplate = builtInTemplate;
        this.templateSource = templateSource;
        this.registryNumberAssigner = registryNumberAssigner;
        this.renderer = renderer;
        this.fileStore = fileStore;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GraduationCertificateResponse issue(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationCertificate saved = create(kind, enrollmentId);
        eventPublisher.issued(saved);
        return toResponse(saved, verificationUrl.of(saved.certificateCode()));
    }

    @Transactional
    public GraduationCertificateResponse issueReplacement(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationCertificate saved = create(kind, enrollmentId);
        return toResponse(saved, verificationUrl.of(saved.certificateCode()));
    }

    private GraduationCertificate create(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationSource source = sources.get(kind);
        source.lockForIssuance(enrollmentId);
        source.requireEligible(enrollmentId);
        if (alreadyIssued(kind, enrollmentId)) {
            throw new GraduationCertificateAlreadyIssuedException();
        }
        CertificateCode code = newUniqueCode();
        CertificateTemplateVersion templateVersion = templateSource.currentPublished(kind);
        CertificateSnapshot snapshot = templateSource.snapshotWith(source.snapshotOf(enrollmentId), templateVersion);
        String registryNumber = registryNumberAssigner.next(kind);
        String url = verificationUrl.of(code);
        byte[] pdf = renderer.render(builtInTemplate.resolve(kind, null), snapshot, code, url,
                templateSource.assetsOf(templateVersion, registryNumber));
        String path = fileStore.save(code.value(), pdf);
        try {
            GraduationCertificate saved = repository.saveAndFlush(GraduationCertificate.issue(kind, enrollmentId,
                    source.studentIdOf(enrollmentId), code, snapshot, digest.sha256(pdf), path,
                    LocalDateTime.now(clock), currentActorFinder.currentPersonId(), registryNumber, templateVersion.getId()));
            return saved;
        } catch (DataIntegrityViolationException e) {
            fileStore.deleteIfRolledBack(path);
            if (violatesValidCertificateUniqueness(e)) {
                throw new GraduationCertificateAlreadyIssuedException();
            }
            throw e;
        }
    }

    private boolean violatesValidCertificateUniqueness(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && (message.contains("uq_graduation_certificate_program_valid")
                || message.contains("uq_graduation_certificate_course_valid"));
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
