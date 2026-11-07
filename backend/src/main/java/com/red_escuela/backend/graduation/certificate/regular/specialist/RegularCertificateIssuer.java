package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.ValidityDays;
import com.red_escuela.backend.graduation.certificate.regular.exception.ConcurrentRegularCertificateIssueException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NotRegularException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingConfirmation;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingResolution;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.events.RegularCertificateIssuedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RegularCertificateIssuer {

    private static final int CODE_ATTEMPTS = 3;

    private final RegularStudentCertificateRepository certificateRepository;
    private final RegularCertificateRequestRepository requestRepository;
    private final RegularityEvaluator regularityEvaluator;
    private final RegularStandingGate standingGate;
    private final RegularEnrollmentFactsFinder factsFinder;
    private final RegularCertificateTemplate template;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ValidityDays validityDays;
    private final SecureRandom random = new SecureRandom();

    public RegularCertificateIssuer(RegularStudentCertificateRepository certificateRepository,
                                    RegularCertificateRequestRepository requestRepository,
                                    RegularityEvaluator regularityEvaluator, RegularStandingGate standingGate,
                                    RegularEnrollmentFactsFinder factsFinder, RegularCertificateTemplate template,
                                    CertificatePdfRenderer renderer, CertificateFileStore fileStore,
                                    CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                    CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry, Clock clock,
                                    @Value("${app.certificate.regular.validity-days}") int validityDays) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
        this.regularityEvaluator = regularityEvaluator;
        this.standingGate = standingGate;
        this.factsFinder = factsFinder;
        this.template = template;
        this.renderer = renderer;
        this.fileStore = fileStore;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.validityDays = new ValidityDays(validityDays);
    }

    @Transactional(noRollbackFor = {NotRegularException.class, RegularCertificateAlreadyIssuedException.class,
            StudentHasDebtException.class, StandingNotConfirmedException.class, StandingEvidenceInvalidException.class})
    public RegularCertificateResponse issue(IssueRegularCertificateCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        RegularEnrollmentFacts facts = factsFinder.find(command.kind(), command.enrollmentId());
        Long studentId = facts.student().getId();
        Long actorId = currentActorFinder.currentPersonId();
        rejectIfStillValid(command, studentId, actorId, now);
        RegularityVerdict verdict = regularityEvaluator.evaluate(command.kind(), command.enrollmentId());
        if (!verdict.regular()) {
            recordRequest(command, studentId, actorId, now, RegularRequestOutcome.NOT_REGULAR,
                    verdict.reasons().stream().map(RegularityReason::name).toList());
            throw new NotRegularException();
        }
        StandingResolution standing = resolveStanding(command, studentId, actorId, now);
        return create(command, facts, standing, actorId, now);
    }

    private void rejectIfStillValid(IssueRegularCertificateCommand command, Long studentId, Long actorId, LocalDateTime now) {
        Optional<RegularStudentCertificate> current = findValid(command);
        if (current.isEmpty()) {
            return;
        }
        if (current.get().isExpiredAt(now)) {
            current.get().expire();
            certificateRepository.saveAndFlush(current.get());
            return;
        }
        recordRequest(command, studentId, actorId, now, RegularRequestOutcome.ALREADY_ISSUED, List.of());
        throw new RegularCertificateAlreadyIssuedException();
    }

    private Optional<RegularStudentCertificate> findValid(IssueRegularCertificateCommand command) {
        return command.kind() == RegularityKind.PROGRAM
                ? certificateRepository.findByProgramEnrollmentIdAndStatus(command.enrollmentId(), RegularCertificateStatus.VALID)
                : certificateRepository.findByCourseEnrollmentIdAndStatus(command.enrollmentId(), RegularCertificateStatus.VALID);
    }

    private StandingResolution resolveStanding(IssueRegularCertificateCommand command, Long studentId, Long actorId, LocalDateTime now) {
        try {
            return standingGate.resolve(studentId, command.kind(), command.enrollmentId(),
                    new StandingConfirmation(command.standingConfirmed(), command.standingEvidence()));
        } catch (StudentHasDebtException | StandingNotConfirmedException | StandingEvidenceInvalidException e) {
            recordRequest(command, studentId, actorId, now, RegularRequestOutcome.REJECTED, List.of(reasonCodeOf(e)));
            throw e;
        }
    }

    private String reasonCodeOf(RuntimeException e) {
        return e.getClass().getSimpleName().replace("Exception", "").replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private RegularCertificateRequest recordRequest(IssueRegularCertificateCommand command, Long studentId, Long actorId,
                                                    LocalDateTime now, RegularRequestOutcome outcome, List<String> reasons) {
        return requestRepository.saveAndFlush(RegularCertificateRequest.record(command.kind(), command.enrollmentId(), studentId,
                actorId, now, command.channel(), outcome, reasons));
    }

    private RegularCertificateResponse create(IssueRegularCertificateCommand command, RegularEnrollmentFacts facts,
                                              StandingResolution standing, Long actorId, LocalDateTime now) {
        Long studentId = facts.student().getId();
        CertificateCode code = newUniqueCode();
        CertificateSnapshot snapshot = template.snapshotOf(facts, now.toLocalDate());
        LocalDateTime expiresAt = now.plusDays(validityDays.value());
        String url = verificationUrl.of(code);
        byte[] pdf = renderer.render(template.definitionFor(snapshot, expiresAt.toLocalDate()), snapshot, code, url);
        String path = fileStore.save(code.value(), pdf);
        try {
            RegularCertificateRequest request = recordRequest(command, studentId, actorId, now, RegularRequestOutcome.ISSUED, List.of());
            RegularStudentCertificate saved = certificateRepository.saveAndFlush(RegularStudentCertificate.builder()
                    .code(code.value())
                    .kind(command.kind())
                    .programEnrollmentId(command.kind() == RegularityKind.PROGRAM ? command.enrollmentId() : null)
                    .courseEnrollmentId(command.kind() == RegularityKind.COURSE ? command.enrollmentId() : null)
                    .studentId(studentId)
                    .status(RegularCertificateStatus.VALID)
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .validityDays(validityDays.value())
                    .issuedById(actorId)
                    .issueChannel(command.channel())
                    .requestId(request.getId())
                    .billingModeAtIssue(standing.mode().name())
                    .standingBasis(standing.basis())
                    .standingConfirmedById(standing.confirmedById())
                    .standingConfirmedAt(standing.confirmedAt())
                    .standingEvidence(standing.evidence())
                    .snapshot(snapshot)
                    .templateRef(snapshot.templateRef())
                    .pdfSha256(digest.sha256(pdf))
                    .pdfPath(path)
                    .build());
            request.linkCertificate(saved.getId());
            requestRepository.save(request);
            eventPublisher.publishEvent(RegularCertificateIssuedEvent.issued(saved.getId(), studentId));
            meterRegistry.counter("regular_certificate_issued_total", "basis", standing.basis().name()).increment();
            log.info("Constancia de regular emitida: certificateId={}, kind={}, standingBasis={}, billingMode={}, channel={}",
                    saved.getId(), command.kind(), standing.basis(), standing.mode(), command.channel());
            return toResponse(saved, url);
        } catch (DataIntegrityViolationException e) {
            fileStore.deleteIfRolledBack(path);
            throw new ConcurrentRegularCertificateIssueException();
        }
    }

    private CertificateCode newUniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            CertificateCode candidate = CertificateCode.generate(random);
            if (!certificateRepository.existsByCode(candidate.value())) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de certificado único");
    }

    private RegularCertificateResponse toResponse(RegularStudentCertificate certificate, String url) {
        return new RegularCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getKind(),
                certificate.enrollmentId(), certificate.getStatus(), certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getStandingBasis(), certificate.getBillingModeAtIssue(), url);
    }
}
