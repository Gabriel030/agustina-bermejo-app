package com.red_escuela.backend.e34b;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentCertificateRevoker;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificatesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class RegularCertificateListingIT extends ReproductionFixture {

    @Autowired
    private ListRegularCertificatesUseCase listUseCase;
    @Autowired
    private RegularEnrollmentCertificateRevoker revoker;

    private IssuedCertificateRows rows;
    private Student student;
    private Long issuerId;
    private Long validId;
    private Long revokedId;
    private Long expiredId;

    @BeforeEach
    void createCertificates() {
        rows = new IssuedCertificateRows(entityManager);
        student = student();
        issuerId = person().getId();
        ProgramEnrollment validEnrollment = programEnrollment(student, program(4), ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment revokedEnrollment = programEnrollment(student, program(4), ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment expiredEnrollment = programEnrollment(student, program(4), ProgramEnrollmentStatus.ENROLLED);
        validId = rows.validRegular(student.getId(), validEnrollment.getId(), issuerId);
        revokedId = rows.validRegular(student.getId(), revokedEnrollment.getId(), issuerId);
        expiredId = rows.validRegular(student.getId(), expiredEnrollment.getId(), issuerId);
        entityManager.createNativeQuery("update regular_student_certificate set expires_at = now() - interval '1 day' where id = :id")
                .setParameter("id", expiredId).executeUpdate();
        entityManager.flush();
        revoker.revokeValidCertificate(RegularityKind.PROGRAM, revokedEnrollmentId(revokedId), "Motivo");
        flushAndClear();
    }

    private Long revokedEnrollmentId(Long certificateId) {
        return ((Number) entityManager.createNativeQuery("select program_enrollment_id from regular_student_certificate where id = :id")
                .setParameter("id", certificateId).getSingleResult()).longValue();
    }

    private java.util.List<Long> idsOf(CertificatePage<RegularCertificateResponse> page) {
        return page.items().stream().map(RegularCertificateResponse::id).toList();
    }

    @Test
    void statusFilterIsAppliedInTheDatabaseForEachStatus() {
        assertThat(idsOf(listUseCase.listPage(student.getId(), RegularCertificateStatus.VALID, 0, 10))).containsExactly(validId);
        assertThat(idsOf(listUseCase.listPage(student.getId(), RegularCertificateStatus.EXPIRED, 0, 10))).containsExactly(expiredId);
        assertThat(idsOf(listUseCase.listPage(student.getId(), RegularCertificateStatus.REVOKED, 0, 10))).containsExactly(revokedId);
    }

    @Test
    void pagesAreSlicedInTheDatabaseAndReportTheUnpagedTotal() {
        CertificatePage<RegularCertificateResponse> firstPage = listUseCase.listPage(student.getId(), null, 0, 2);
        CertificatePage<RegularCertificateResponse> secondPage = listUseCase.listPage(student.getId(), null, 1, 2);

        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(idsOf(firstPage)).doesNotContainAnyElementsOf(idsOf(secondPage));
    }

    @Test
    void listingWithoutPageParametersStillReturnsEveryCertificateOfTheStudent() {
        assertThat(listUseCase.list(student.getId(), null)).hasSize(3);
    }
}
