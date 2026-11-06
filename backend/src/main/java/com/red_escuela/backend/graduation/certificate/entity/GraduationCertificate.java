package com.red_escuela.backend.graduation.certificate.entity;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "graduation_certificate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraduationCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 16, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private GraduationCertificateKind kind;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GraduationCertificateStatus status;

    @Column(name = "graduation_date", nullable = false, updatable = false)
    private LocalDate graduationDate;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by_id", nullable = false, updatable = false)
    private Long issuedById;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private CertificateSnapshot snapshot;

    @Column(name = "template_ref", nullable = false, length = 40, updatable = false)
    private String templateRef;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "pdf_path", nullable = false, length = 300)
    private String pdfPath;

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    public static GraduationCertificate issue(GraduationCertificateKind kind, Long enrollmentId, Long studentId,
                                              CertificateCode code, CertificateSnapshot snapshot, String pdfSha256,
                                              String pdfPath, LocalDateTime issuedAt, Long issuedById) {
        return GraduationCertificate.builder()
                .code(code.value())
                .kind(kind)
                .programEnrollmentId(kind == GraduationCertificateKind.PROGRAM ? enrollmentId : null)
                .courseEnrollmentId(kind == GraduationCertificateKind.COURSE ? enrollmentId : null)
                .studentId(studentId)
                .status(GraduationCertificateStatus.VALID)
                .graduationDate(snapshot.graduationDate())
                .issuedAt(issuedAt)
                .issuedById(issuedById)
                .snapshot(snapshot)
                .templateRef(snapshot.templateRef())
                .pdfSha256(pdfSha256)
                .pdfPath(pdfPath)
                .build();
    }

    public void replacePdf(String newSha256, String newPath) {
        this.pdfSha256 = newSha256;
        this.pdfPath = newPath;
    }

    public CertificateCode certificateCode() {
        return new CertificateCode(code);
    }
}
