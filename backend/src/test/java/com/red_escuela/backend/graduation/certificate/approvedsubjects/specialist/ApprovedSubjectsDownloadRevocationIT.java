package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotAvailableException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectLine;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.ApprovedSubjectsCertificateRevoker;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ApprovedSubjectsDownloadRevocationIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final LocalDateTime EXPIRES_AT = NOW.plusDays(30);
    private static final String DOCUMENT_TYPE = "APPROVED_SUBJECTS_CERTIFICATE";
    private static final String WRONG_SHA256 = "b".repeat(64);

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApprovedSubjectsCertificateRepository repository;
    @Autowired
    private ApprovedSubjectsCertificatePdfProvider pdfProvider;
    @Autowired
    private ApprovedSubjectsCertificateExpirer expirer;
    @Autowired
    private ApprovedSubjectsCertificateRepositoryStore store;
    @Autowired
    private ApprovedSubjectsCertificateResponseAssembler responses;
    @Autowired
    private ApprovedSubjectsCertificateAuditLog auditLog;
    @Autowired
    private ApprovedSubjectsPdfRenderer renderer;
    @Autowired
    private CertificateDigest digest;
    @Autowired
    private Clock clock;

    private final Long studentId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
    private final Long otherStudentId = studentId + 1;
    private final Long enrollmentId = studentId + 2;

    @BeforeEach
    void freezeClock() {
        deleteRows();
        MutableClock.INSTANCE.setDateTime(NOW);
    }

    @AfterEach
    void cleanUp() {
        deleteRows();
        MutableClock.INSTANCE.reset();
    }

    private void deleteRows() {
        jdbc.update("delete from verifiable_document where document_type = ?", DOCUMENT_TYPE);
        jdbc.update("delete from approved_subjects_certificate");
        jdbc.update("delete from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action in ('DOCUMENT_PDF_DOWNLOADED', "
                + "'DOCUMENT_PDF_HASH_MISMATCH', 'APPROVED_SUBJECTS_CERT_REVOKED')");
    }

    private ApprovedSubjectsSnapshot snapshot() {
        return new ApprovedSubjectsSnapshot("PÉREZ, ANA", "DNI", "30123456", "Tecnicatura en Enfermería", "Instituto",
                List.of(new ApprovedSubjectLine("Anatomía", 1, new BigDecimal("8.00"), LocalDate.of(2026, 5, 20))), 1, 4,
                new BigDecimal("25.00"), new BigDecimal("8.00"), null, null, NOW.toLocalDate(), EXPIRES_AT.toLocalDate(), List.of(),
                "APPROVED_SUBJECTS_BUILT_IN_V1");
    }

    private ApprovedSubjectsCertificate insert(Long owner, String sha256Override, LocalDateTime expiresAt) {
        ApprovedSubjectsSnapshot snapshot = snapshot();
        CertificateCode code = CertificateCode.generate(new SecureRandom());
        String sha256 = sha256Override != null ? sha256Override : digest.sha256(renderer.render(snapshot, code));
        ApprovedSubjectsCertificate certificate = ApprovedSubjectsCertificate.createValid(enrollmentId, owner, code, snapshot, sha256,
                ApprovedSubjectsPdfRenderer.ENGINE_VERSION, null, NOW, 30, 1L);
        return transactions.execute(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            ApprovedSubjectsCertificate saved = repository.saveAndFlush(certificate);
            jdbc.update("update approved_subjects_certificate set expires_at = ? where id = ?", expiresAt, saved.getId());
            return saved;
        });
    }

    private long auditCount(String action, Long id) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_type = 'CERTIFICATE_DOCUMENT' and entity_id = ?",
                Long.class, action, id);
    }

    private ApprovedSubjectsCertificateRevoker revoker() {
        ApprovedSubjectsActor actor = new ApprovedSubjectsActor() {
            @Override
            public Long currentStudentId() {
                return studentId;
            }

            @Override
            public Long currentPersonId() {
                return 77L;
            }
        };
        return new ApprovedSubjectsCertificateRevoker(store, responses, actor, auditLog, clock);
    }

    private void revokeInTransaction(Long id) {
        transactions.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            revoker().revoke(id, "emitida por error");
        });
    }

    @Test
    void ownDownloadReturnsAStableHashMatchingPdfAndAuditsIt() {
        ApprovedSubjectsCertificate certificate = insert(studentId, null, EXPIRES_AT);

        ApprovedSubjectsCertificatePdf first = pdfProvider.pdfOfOwn(certificate.getId(), studentId);
        ApprovedSubjectsCertificatePdf second = pdfProvider.pdfOfOwn(certificate.getId(), studentId);

        assertThat(first.code()).isEqualTo(certificate.getCode());
        assertThat(digest.sha256(first.content())).isEqualTo(certificate.getPdfSha256()).isEqualTo(digest.sha256(second.content()));
        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", certificate.getId())).isEqualTo(2L);
    }

    @Test
    void anotherStudentsCertificateIsNotFoundAndNothingIsAudited() {
        ApprovedSubjectsCertificate certificate = insert(studentId, null, EXPIRES_AT);

        assertThatThrownBy(() -> pdfProvider.pdfOfOwn(certificate.getId(), otherStudentId))
                .isInstanceOf(ApprovedSubjectsCertificateNotFoundException.class);
        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", certificate.getId())).isZero();
    }

    @Test
    void hashMismatchIsAuditedAndNoDifferentPdfIsServed() {
        ApprovedSubjectsCertificate certificate = insert(studentId, WRONG_SHA256, EXPIRES_AT);

        assertThatThrownBy(() -> pdfProvider.pdfOfOwn(certificate.getId(), studentId))
                .isInstanceOf(CertificatePdfIntegrityMismatchException.class);
        assertThat(auditCount("DOCUMENT_PDF_HASH_MISMATCH", certificate.getId())).isEqualTo(1L);
        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", certificate.getId())).isZero();
    }

    @Test
    void expiredAndRevokedCertificatesCannotBeDownloaded() {
        ApprovedSubjectsCertificate expired = insert(studentId, null, NOW);
        ApprovedSubjectsCertificate revoked = insert(studentId, null, EXPIRES_AT);
        revokeInTransaction(revoked.getId());

        assertThatThrownBy(() -> pdfProvider.pdfOfOwn(expired.getId(), studentId)).isInstanceOf(ApprovedSubjectsCertificateNotFoundException.class);
        assertThatThrownBy(() -> pdfProvider.pdfOfOwn(revoked.getId(), studentId)).isInstanceOf(ApprovedSubjectsCertificateNotFoundException.class);
    }

    @Test
    void revokingAValidCertificateStoresTheRevocationAndAuditsItInTheSameTransaction() {
        ApprovedSubjectsCertificate certificate = insert(studentId, null, EXPIRES_AT);

        revokeInTransaction(certificate.getId());

        ApprovedSubjectsCertificate stored = repository.findById(certificate.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.REVOKED);
        assertThat(stored.getRevokedById()).isEqualTo(77L);
        assertThat(stored.getRevocationReason()).isEqualTo("emitida por error");
        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        assertThat(auditCount("APPROVED_SUBJECTS_CERT_REVOKED", certificate.getId())).isEqualTo(1L);
    }

    @Test
    void revokingAnAlreadyRevokedCertificateIsNotAvailableAndAuditsNothingMore() {
        ApprovedSubjectsCertificate certificate = insert(studentId, null, EXPIRES_AT);
        revokeInTransaction(certificate.getId());

        assertThatThrownBy(() -> revokeInTransaction(certificate.getId())).isInstanceOf(ApprovedSubjectsCertificateNotAvailableException.class);
        assertThat(auditCount("APPROVED_SUBJECTS_CERT_REVOKED", certificate.getId())).isEqualTo(1L);
    }

    @Test
    void expirerMovesOnlyCertificatesWhoseExpiryHasBeenReachedIncludingTheExactInstant() {
        ApprovedSubjectsCertificate atBoundary = insert(studentId, null, NOW);
        ApprovedSubjectsCertificate justAfter = insert(studentId, null, NOW.plusSeconds(1));
        ApprovedSubjectsCertificate past = insert(studentId, null, NOW.minusDays(1));

        int expired = expirer.expireDue();

        assertThat(expired).isEqualTo(2);
        assertThat(repository.findById(atBoundary.getId()).orElseThrow().getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.EXPIRED);
        assertThat(repository.findById(past.getId()).orElseThrow().getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.EXPIRED);
        assertThat(repository.findById(justAfter.getId()).orElseThrow().getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.VALID);
    }

    @Test
    void expirerLeavesRevokedCertificatesRevoked() {
        ApprovedSubjectsCertificate certificate = insert(studentId, null, NOW.minusDays(1));
        revokeInTransaction(certificate.getId());

        assertThat(expirer.expireDue()).isZero();
        assertThat(repository.findById(certificate.getId()).orElseThrow().getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.REVOKED);
    }
}
