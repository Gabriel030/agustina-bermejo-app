package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateDocumentKind;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import com.red_escuela.backend.graduation.certificate.verification.specialist.GraduationVerificationSource;
import com.red_escuela.backend.graduation.certificate.verification.specialist.RegularVerificationSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationSourcesTest {

    private static final CertificateCode CODE = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 12, 5, 10, 0);

    private final CertificateSnapshot snapshot = new CertificateSnapshot("PÉREZ, ANA", "DNI", "30123456", "la carrera",
            "Tecnicatura en Enfermería", "UNDERGRADUATE", 1860, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto",
            List.of(new SignatorySnapshot("Dirección", "Dirección académica")), "BUILT_IN_V1");

    private RegularVerificationSource regularSourceWith(RegularCertificateStatus status) {
        RegularStudentCertificateRepository repository = mock(RegularStudentCertificateRepository.class);
        RegularStudentCertificate certificate = RegularStudentCertificate.builder()
                .code(CODE.value()).status(status).snapshot(snapshot).issuedAt(LocalDateTime.of(2026, 11, 5, 10, 0))
                .expiresAt(EXPIRES_AT).revokedAt(LocalDateTime.of(2026, 11, 20, 9, 0)).registryNumber("1284")
                .standingEvidence("recibo 1234 verificado").revocationReason("error de carga").build();
        when(repository.findByCode(CODE.value())).thenReturn(Optional.of(certificate));
        return new RegularVerificationSource(repository);
    }

    private PublicCertificateView regularAt(RegularCertificateStatus status, LocalDateTime now) {
        return regularSourceWith(status).findByCode(CODE, now).orElseThrow();
    }

    @Test
    void validRegularCertificateIsValidBeforeItsExpiry() {
        PublicCertificateView view = regularAt(RegularCertificateStatus.VALID, EXPIRES_AT.minusSeconds(1));

        assertThat(view.status()).isEqualTo(PublicCertificateStatus.VALID);
        assertThat(view.documentKind()).isEqualTo(PublicCertificateDocumentKind.REGULAR);
        assertThat(view.maskedDocument()).isEqualTo("*****456");
        assertThat(view.revokedAt()).isNull();
        assertThat(view.graduationDate()).isNull();
    }

    @Test
    void validFlagPastItsExpiryDateIsReportedAsExpiredWithoutWaitingForTheNightlyJob() {
        PublicCertificateView view = regularAt(RegularCertificateStatus.VALID, EXPIRES_AT.plusSeconds(1));

        assertThat(view.status()).isEqualTo(PublicCertificateStatus.EXPIRED);
        assertThat(view.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void certificateExpiringExactlyNowIsStillValid() {
        assertThat(regularAt(RegularCertificateStatus.VALID, EXPIRES_AT).status()).isEqualTo(PublicCertificateStatus.VALID);
    }

    @Test
    void expiredFlagIsReportedAsExpired() {
        assertThat(regularAt(RegularCertificateStatus.EXPIRED, EXPIRES_AT.minusDays(3)).status())
                .isEqualTo(PublicCertificateStatus.EXPIRED);
    }

    @Test
    void revokedRegularCertificateIsRevokedEvenAfterItsExpiry() {
        PublicCertificateView view = regularAt(RegularCertificateStatus.REVOKED, EXPIRES_AT.plusDays(30));

        assertThat(view.status()).isEqualTo(PublicCertificateStatus.REVOKED);
        assertThat(view.revokedAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
    }

    @Test
    void graduationCertificateIsNeverExpiredAndRevocationIsReported() {
        GraduationCertificateRepository repository = mock(GraduationCertificateRepository.class);
        GraduationVerificationSource source = new GraduationVerificationSource(repository);
        GraduationCertificate valid = GraduationCertificate.issue(GraduationCertificateKind.PROGRAM, 42L, 7L, CODE, snapshot,
                "a".repeat(64), "x.pdf", LocalDateTime.of(2026, 11, 5, 10, 0), 3L);
        when(repository.findByCode(CODE.value())).thenReturn(Optional.of(valid));

        PublicCertificateView view = source.findByCode(CODE, LocalDateTime.of(2040, 1, 1, 0, 0)).orElseThrow();

        assertThat(view.status()).isEqualTo(PublicCertificateStatus.VALID);
        assertThat(view.documentKind()).isEqualTo(PublicCertificateDocumentKind.GRADUATION);
        assertThat(view.expiresAt()).isNull();
        assertThat(view.graduationDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(view.hours()).isEqualTo(1860);

        GraduationCertificate revoked = GraduationCertificate.builder().code(CODE.value()).status(GraduationCertificateStatus.REVOKED)
                .snapshot(snapshot).issuedAt(LocalDateTime.of(2026, 11, 5, 10, 0)).revokedAt(LocalDateTime.of(2026, 11, 9, 8, 0)).build();
        when(repository.findByCode(CODE.value())).thenReturn(Optional.of(revoked));

        PublicCertificateView revokedView = source.findByCode(CODE, LocalDateTime.of(2026, 11, 10, 0, 0)).orElseThrow();

        assertThat(revokedView.status()).isEqualTo(PublicCertificateStatus.REVOKED);
        assertThat(revokedView.revokedAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 8, 0));
    }

    @Test
    void publicViewHasNoFieldForIssuerEvidenceRevocationReasonOrContact() {
        List<String> fields = java.util.Arrays.stream(PublicCertificateView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(fields).containsExactlyInAnyOrder("documentKind", "status", "holderFullName", "maskedDocument", "titleKind",
                "titleName", "graduationDate", "issuedAt", "expiresAt", "revokedAt", "registryNumber", "hours");
    }
}
