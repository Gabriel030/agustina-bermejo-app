package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSubmissionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.ValidityDays;
import com.red_escuela.backend.graduation.certificate.regular.exception.ConcurrentRegularCertificateIssueException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NotRegularException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceRequiredException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentDocumentMissingException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingConfirmation;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate.StandingResolution;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.events.RegularCertificateIssuedEvent;
import com.red_escuela.backend.graduation.events.RegularCertificateRequestFulfilledEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
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
    private final RegularCertificateSettingsFinder settingsFinder;
    private final CertificateExceptionConsumer exceptionConsumer;
    private final VerifiableDocumentRegistry verifiableDocumentRegistry;
    private final CertificateTemplateRenderSource templateSource;
    private final SecureRandom random = new SecureRandom();

    public RegularCertificateIssuer(RegularStudentCertificateRepository certificateRepository,
                                    RegularCertificateRequestRepository requestRepository,
                                    RegularityEvaluator regularityEvaluator, RegularStandingGate standingGate,
                                    RegularEnrollmentFactsFinder factsFinder, RegularCertificateTemplate template,
                                    CertificatePdfRenderer renderer, CertificateFileStore fileStore,
                                    CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                    CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry, Clock clock,
                                    RegularCertificateSettingsFinder settingsFinder,
                                    CertificateExceptionConsumer exceptionConsumer,
                                    VerifiableDocumentRegistry verifiableDocumentRegistry,
                                    CertificateTemplateRenderSource templateSource) {
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
        this.settingsFinder = settingsFinder;
        this.exceptionConsumer = exceptionConsumer;
        this.verifiableDocumentRegistry = verifiableDocumentRegistry;
        this.templateSource = templateSource;
    }

    @Transactional(noRollbackFor = {NotRegularException.class, RegularCertificateAlreadyIssuedException.class,
            StudentHasDebtException.class, StandingNotConfirmedException.class, StandingEvidenceInvalidException.class,
            StandingEvidenceRequiredException.class, NoDebtToExceptException.class})
    public RegularCertificateResponse issue(IssueRegularCertificateCommand command) {
        IssuanceContext context = prepare(command);
        StandingResolution standing = resolveStanding(command, context.studentId(), context.actorId(), context.now());
        return create(command, context.facts(), standing, context.actorId(), context.now());
    }

    @Transactional(noRollbackFor = {NotRegularException.class, RegularCertificateAlreadyIssuedException.class,
            StudentHasDebtException.class, StandingNotConfirmedException.class, StandingEvidenceInvalidException.class,
            StandingEvidenceRequiredException.class, NoDebtToExceptException.class})
    public RegularCertificateSubmissionResponse submitFromPortal(IssueRegularCertificateCommand command) {
        IssuanceContext context = prepare(command);
        if (standingGate.requiresStaffConfirmation()) {
            return RegularCertificateSubmissionResponse.pending(RegularRequestResponses.of(
                    pendingRequestOf(command, context.studentId(), context.actorId(), context.now()), context.facts()));
        }
        StandingResolution standing = resolveStanding(command, context.studentId(), context.actorId(), context.now());
        return RegularCertificateSubmissionResponse.issued(create(command, context.facts(), standing, context.actorId(), context.now()));
    }

    @Transactional(noRollbackFor = {NotRegularException.class, RegularCertificateAlreadyIssuedException.class,
            StudentHasDebtException.class, StandingNotConfirmedException.class})
    public RegularCertificateResponse issueToSelf(IssueRegularCertificateCommand command) {
        IssuanceContext context = prepare(command);
        StandingResolution standing = resolveSelfIssueStanding(command, context.studentId(), context.actorId(), context.now());
        return create(command, context.facts(), standing, context.actorId(), context.now());
    }

    @Transactional(noRollbackFor = {NotRegularException.class, RegularCertificateAlreadyIssuedException.class,
            NoDebtToExceptException.class})
    public RegularCertificateResponse issueUnderDebtException(Long studentId, Long enrollmentId) {
        IssueRegularCertificateCommand command = new IssueRegularCertificateCommand(RegularityKind.PROGRAM, enrollmentId, false, null,
                RegularCertificateChannel.STAFF, null, null);
        factsFinder.findOwnedBy(RegularityKind.PROGRAM, enrollmentId, studentId);
        IssuanceContext context = prepare(command);
        StandingResolution standing = resolveDebtExceptionStanding(command, context);
        return create(command, context.facts(), standing, context.actorId(), context.now());
    }

    private StandingResolution resolveDebtExceptionStanding(IssueRegularCertificateCommand command, IssuanceContext context) {
        try {
            return standingGate.resolveByException(context.studentId(), command.kind(), command.enrollmentId());
        } catch (NoDebtToExceptException e) {
            recordRequest(command, context.studentId(), context.actorId(), context.now(), RegularRequestOutcome.REJECTED,
                    List.of(reasonCodeOf(e)));
            throw e;
        }
    }

    private IssuanceContext prepare(IssueRegularCertificateCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        RegularEnrollmentFacts facts = factsFinder.find(command.kind(), command.enrollmentId());
        requireDocument(facts);
        Long studentId = facts.student().getId();
        Long actorId = currentActorFinder.currentPersonId();
        rejectIfStillValid(command, studentId, actorId, now);
        requireRegular(command, studentId, actorId, now);
        return new IssuanceContext(facts, studentId, actorId, now);
    }

    private record IssuanceContext(RegularEnrollmentFacts facts, Long studentId, Long actorId, LocalDateTime now) {
    }

    private StandingResolution resolveSelfIssueStanding(IssueRegularCertificateCommand command, Long studentId, Long actorId,
                                                        LocalDateTime now) {
        try {
            return standingGate.resolveForSelfIssue(studentId, command.kind(), command.enrollmentId());
        } catch (StudentHasDebtException e) {
            recordRequest(command, studentId, actorId, now, RegularRequestOutcome.REJECTED, List.of(reasonCodeOf(e)));
            throw e;
        }
    }

    private void requireDocument(RegularEnrollmentFacts facts) {
        String documentNumber = facts.student().getPerson().getDni();
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new StudentDocumentMissingException();
        }
    }

    private void requireRegular(IssueRegularCertificateCommand command, Long studentId, Long actorId, LocalDateTime now) {
        RegularityVerdict verdict = regularityEvaluator.evaluate(command.kind(), command.enrollmentId());
        if (!verdict.institutionallyRegular()) {
            recordRequest(command, studentId, actorId, now, RegularRequestOutcome.NOT_REGULAR,
                    verdict.reasons().stream().map(RegularityReason::name).toList());
            throw new NotRegularException();
        }
    }

    private RegularCertificateRequest pendingRequestOf(IssueRegularCertificateCommand command, Long studentId, Long actorId,
                                                       LocalDateTime now) {
        return findPendingRequest(command).orElseGet(() -> recordRequest(command, studentId, actorId, now,
                RegularRequestOutcome.PENDING_STAFF, List.of()));
    }

    private Optional<RegularCertificateRequest> findPendingRequest(IssueRegularCertificateCommand command) {
        return command.kind() == RegularityKind.PROGRAM
                ? requestRepository.findFirstByProgramEnrollmentIdAndOutcomeOrderByRequestedAtDesc(command.enrollmentId(), RegularRequestOutcome.PENDING_STAFF)
                : requestRepository.findFirstByCourseEnrollmentIdAndOutcomeOrderByRequestedAtDesc(command.enrollmentId(), RegularRequestOutcome.PENDING_STAFF);
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
            if (command.exceptionId() != null) {
                exceptionConsumer.requireUsable(command.exceptionId(), studentId, command.kind(), command.enrollmentId());
                return standingGate.resolveByException(studentId, command.kind(), command.enrollmentId());
            }
            return standingGate.resolve(studentId, command.kind(), command.enrollmentId(),
                    new StandingConfirmation(command.standingConfirmed(), command.standingEvidence()));
        } catch (StudentHasDebtException | StandingNotConfirmedException | StandingEvidenceInvalidException
                 | StandingEvidenceRequiredException | NoDebtToExceptException e) {
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
        ValidityDays validityDays = settingsFinder.current().validityDays();
        LocalDateTime expiresAt = now.plusDays(validityDays.value());
        CertificateSnapshot builtInSnapshot = template.snapshotOf(facts, now.toLocalDate()).withRecipientLabel(command.recipientLabel());
        Optional<CertificateTemplateVersion> publishedVersion = templateSource.findCurrentPublished(DocumentTemplateType.REGULAR_STUDENT_CERTIFICATE);
        CertificateSnapshot snapshot = publishedVersion
                .map(version -> templateSource.snapshotWith(builtInSnapshot.withExtraMarkers(template.extraMarkersOf(command.recipientLabel(), expiresAt.toLocalDate())), version)
                        .withRecipientLabel(command.recipientLabel()))
                .orElse(builtInSnapshot);
        String url = verificationUrl.of(code);
        CertificateTemplateDefinition definition = template.definitionFor(snapshot, expiresAt.toLocalDate());
        byte[] pdf = publishedVersion
                .map(version -> renderer.render(definition, snapshot, code, url, templateSource.assetsOf(version, null)))
                .orElseGet(() -> renderer.render(definition, snapshot, code, url));
        String path = fileStore.save(code.value(), pdf);
        try {
            Optional<RegularCertificateRequest> pending = findPendingRequest(command);
            RegularCertificateRequest request = pending.orElseGet(() -> recordRequest(command, studentId, actorId, now,
                    RegularRequestOutcome.ISSUED, List.of()));
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
                    .exceptionId(command.exceptionId())
                    .snapshot(snapshot)
                    .templateRef(snapshot.templateRef())
                    .templateVersionId(publishedVersion.map(CertificateTemplateVersion::getId).orElse(null))
                    .pdfSha256(digest.sha256(pdf))
                    .renderEngineVersion(CertificatePdfRenderer.ENGINE_VERSION)
                    .pdfPath(path)
                    .build());
            verifiableDocumentRegistry.register(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, saved.getId(), code);
            consumeExceptionIfPresent(command, saved, studentId, actorId, path);
            request.linkCertificate(saved.getId());
            pending.ifPresent(open -> open.resolveAsIssued(saved.getId()));
            requestRepository.save(request);
            pending.ifPresent(open -> eventPublisher.publishEvent(RegularCertificateRequestFulfilledEvent.of(saved.getId(), studentId,
                    facts.student().getPerson().getId())));
            eventPublisher.publishEvent(RegularCertificateIssuedEvent.issued(saved.getId(), studentId));
            meterRegistry.counter("regular_certificate_issued_total", "basis", standing.basis().name()).increment();
            log.info("Constancia de regular emitida: certificateId={}, kind={}, standingBasis={}, billingMode={}, channel={}",
                    saved.getId(), command.kind(), standing.basis(), standing.mode(), command.channel());
            return toResponse(saved, facts.studentName(), url);
        } catch (DataIntegrityViolationException e) {
            fileStore.deleteIfRolledBack(path);
            throw new ConcurrentRegularCertificateIssueException();
        }
    }

    private void consumeExceptionIfPresent(IssueRegularCertificateCommand command, RegularStudentCertificate saved, Long studentId,
                                           Long actorId, String path) {
        if (command.exceptionId() == null) {
            return;
        }
        try {
            exceptionConsumer.consume(command.exceptionId(), saved.getId(), studentId, command.kind(), command.enrollmentId(), actorId);
        } catch (RuntimeException failure) {
            fileStore.deleteIfRolledBack(path);
            throw failure;
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

    private RegularCertificateResponse toResponse(RegularStudentCertificate certificate, String studentName, String url) {
        return new RegularCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getStudentId(),
                studentName, certificate.getSnapshot().titleName(), certificate.getKind(),
                certificate.enrollmentId(), certificate.getStatus(), certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getStandingBasis(), certificate.getBillingModeAtIssue(), url);
    }
}
