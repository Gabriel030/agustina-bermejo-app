package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGrade;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateIssuing;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectLine;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ApprovedSubjectsCertificateIssuer implements ApprovedSubjectsCertificateIssuing {

    static final int CODE_ATTEMPTS = 3;
    static final String ISSUED_COUNTER = "approved_subjects_certificate_issued_total";

    private final StudentFinder studentFinder;
    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final ApprovedSubjectGradesReader gradesReader;
    private final DocumentIssuanceSettingsFinder settingsFinder;
    private final GraduationSnapshotFactory holderFactory;
    private final ApprovedSubjectsSnapshotFactory snapshotFactory;
    private final ApprovedSubjectsPdfRenderer renderer;
    private final CertificateDigest digest;
    private final ApprovedSubjectsCertificateRepository repository;
    private final VerifiableDocumentRegistry verifiableDocumentRegistry;
    private final AuditTrail auditTrail;
    private final ApprovedSubjectsCertificateResponseAssembler responses;
    private final ApprovedSubjectsRefusals refusals;
    private final MeterRegistry meterRegistry;
    private final CertificateTemplateRenderSource templateSource;
    private final SecureRandom random = new SecureRandom();

    public ApprovedSubjectsCertificateIssuer(StudentFinder studentFinder, ProgramEnrollmentFinder programEnrollmentFinder,
                                             ProgramCurriculumFinder programCurriculumFinder, ApprovedSubjectGradesReader gradesReader,
                                             DocumentIssuanceSettingsFinder settingsFinder, GraduationSnapshotFactory holderFactory,
                                             ApprovedSubjectsSnapshotFactory snapshotFactory, ApprovedSubjectsPdfRenderer renderer,
                                             CertificateDigest digest, ApprovedSubjectsCertificateRepository repository,
                                             VerifiableDocumentRegistry verifiableDocumentRegistry, AuditTrail auditTrail,
                                             ApprovedSubjectsCertificateResponseAssembler responses, ApprovedSubjectsRefusals refusals,
                                             MeterRegistry meterRegistry,
                                             CertificateTemplateRenderSource templateSource) {
        this.studentFinder = studentFinder;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.programCurriculumFinder = programCurriculumFinder;
        this.gradesReader = gradesReader;
        this.settingsFinder = settingsFinder;
        this.holderFactory = holderFactory;
        this.snapshotFactory = snapshotFactory;
        this.renderer = renderer;
        this.digest = digest;
        this.repository = repository;
        this.verifiableDocumentRegistry = verifiableDocumentRegistry;
        this.auditTrail = auditTrail;
        this.responses = responses;
        this.refusals = refusals;
        this.meterRegistry = meterRegistry;
        this.templateSource = templateSource;
    }

    @Override
    @Transactional
    public ApprovedSubjectsCertificateResponse issue(Long studentId, Long enrollmentId, Long issuedById, LocalDateTime now) {
        Student student = studentFinder.requireByIdForUpdate(studentId);
        int validityDays = settingsFinder.current().approvedSubjectsValidityDays();
        requireDailyAllowance(studentId, now);
        ProgramEnrollment enrollment = programEnrollmentFinder.requireOwnedBy(enrollmentId, studentId);
        ApprovedSubjectsSnapshot builtInSnapshot = snapshotOf(student, enrollment, now, validityDays);
        Optional<CertificateTemplateVersion> publishedVersion = templateSource.findCurrentPublished(DocumentTemplateType.APPROVED_SUBJECTS_CERTIFICATE);
        ApprovedSubjectsSnapshot snapshot = publishedVersion.map(version -> withPublishedTemplate(builtInSnapshot, version)).orElse(builtInSnapshot);
        CertificateCode code = newUniqueCode();
        byte[] pdf = publishedVersion
                .map(version -> renderer.render(snapshot, code, templateSource.assetsOf(version, null)))
                .orElseGet(() -> renderer.render(snapshot, code));
        ApprovedSubjectsCertificate saved = repository.saveAndFlush(ApprovedSubjectsCertificate.createValid(enrollmentId, studentId,
                code, snapshot, digest.sha256(pdf), ApprovedSubjectsPdfRenderer.ENGINE_VERSION, null, now, validityDays, issuedById,
                publishedVersion.map(CertificateTemplateVersion::getId).orElse(null)));
        verifiableDocumentRegistry.register(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, saved.getId(), code);
        auditTrail.record(new AuditRecord(AuditAction.APPROVED_SUBJECTS_CERT_ISSUED, AuditEntityType.CERTIFICATE_DOCUMENT, saved.getId(),
                null, saved.getPdfSha256(), "enrollmentId=" + enrollmentId + ";engineVersion=" + saved.getRenderEngineVersion()));
        meterRegistry.counter(ISSUED_COUNTER).increment();
        log.info("Constancia de materias aprobadas emitida: certificateId={}, enrollmentId={}", saved.getId(), enrollmentId);
        return responses.of(saved);
    }

    private ApprovedSubjectsSnapshot withPublishedTemplate(ApprovedSubjectsSnapshot snapshot, CertificateTemplateVersion version) {
        CertificateSnapshot holder = new CertificateSnapshot(snapshot.holderFullName(), snapshot.documentTypeLabel(),
                snapshot.documentNumber(), "carrera", snapshot.programName(), null, null, snapshot.issuedOn(), snapshot.issuedOn(),
                snapshot.institutionName(), snapshot.signatories(), snapshot.templateRef(), null);
        CertificateSnapshot published = templateSource.snapshotWith(holder, version);
        return snapshot.withPublishedTemplate(published.institutionName(), published.signatories(), published.templateRef());
    }

    private ApprovedSubjectsSnapshot snapshotOf(Student student, ProgramEnrollment enrollment, LocalDateTime now, int validityDays) {
        String programName = enrollment.getProgram().getName();
        CertificateSnapshot holder = holderFactory.create(student, "la carrera", programName, null, null, now.toLocalDate());
        List<ApprovedSubjectLine> approved = gradesReader.listApprovedSubjectGrades(student.getId(), enrollment.getId()).stream()
                .map(this::lineOf)
                .toList();
        int totalSubjects = programCurriculumFinder.findProgramSubjects(enrollment.getProgram().getId()).size();
        return snapshotFactory.create(new ApprovedSubjectsSnapshotFactory.Input(holder.holderFullName(), holder.documentTypeLabel(),
                holder.documentNumber(), programName, holder.institutionName(), approved, totalSubjects, null, now.toLocalDate(),
                validityDays, holder.signatories()));
    }

    private ApprovedSubjectLine lineOf(ApprovedSubjectGrade grade) {
        return new ApprovedSubjectLine(grade.subjectName(), grade.curricularYear(), Grade.valueOrNull(grade.grade()), grade.approvedOn());
    }

    private void requireDailyAllowance(Long studentId, LocalDateTime now) {
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        long issuedToday = repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(studentId, dayStart,
                dayStart.plusDays(1));
        if (issuedToday >= settingsFinder.current().dailyIssuanceLimitPerType()) {
            throw refusals.issuanceLimitReached();
        }
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
}
