package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class RegularCertificateSpecifications {

    private RegularCertificateSpecifications() {
    }

    public static Specification<RegularStudentCertificate> certificatesOf(Long studentId, RegularCertificateStatus status, LocalDateTime now) {
        return Specification.where(certificateOfStudent(studentId)).and(certificateWithStatus(status, now));
    }

    public static Specification<RegularCertificateRequest> requestsOf(Long studentId, RegularRequestOutcome outcome) {
        return Specification.where(requestOfStudent(studentId)).and(requestWithOutcome(outcome));
    }

    private static Specification<RegularStudentCertificate> certificateOfStudent(Long studentId) {
        return (root, query, builder) -> studentId == null ? null : builder.equal(root.get("studentId"), studentId);
    }

    private static Specification<RegularStudentCertificate> certificateWithStatus(RegularCertificateStatus status, LocalDateTime now) {
        return (root, query, builder) -> {
            if (status == null) {
                return null;
            }
            return switch (status) {
                case VALID -> builder.and(builder.equal(root.get("status"), RegularCertificateStatus.VALID),
                        builder.greaterThanOrEqualTo(root.get("expiresAt"), now));
                case EXPIRED -> builder.or(builder.equal(root.get("status"), RegularCertificateStatus.EXPIRED),
                        builder.and(builder.equal(root.get("status"), RegularCertificateStatus.VALID),
                                builder.lessThan(root.get("expiresAt"), now)));
                case REVOKED -> builder.equal(root.get("status"), RegularCertificateStatus.REVOKED);
            };
        };
    }

    private static Specification<RegularCertificateRequest> requestOfStudent(Long studentId) {
        return (root, query, builder) -> studentId == null ? null : builder.equal(root.get("studentId"), studentId);
    }

    private static Specification<RegularCertificateRequest> requestWithOutcome(RegularRequestOutcome outcome) {
        return (root, query, builder) -> outcome == null ? null : builder.equal(root.get("outcome"), outcome);
    }
}
