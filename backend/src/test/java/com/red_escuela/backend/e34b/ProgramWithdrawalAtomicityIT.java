package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsEnrollmentCertificateRevoker;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ProgramWithdrawalAtomicityIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @MockitoSpyBean
    private ApprovedSubjectsEnrollmentCertificateRevoker approvedSubjectsRevoker;

    private record Committed(Long programEnrollmentId, Long sectionEnrollmentId, Long regularId, Long approvedId) {
    }

    private Committed commitEnrollmentWithCertificates() {
        return transactionTemplate.execute(status -> {
            AcademicReproductionFixtures fixtures = new AcademicReproductionFixtures(entityManager);
            IssuedCertificateRows rows = new IssuedCertificateRows(entityManager);
            Student student = fixtures.student("76000001");
            ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, fixtures.program("Carrera atomica"),
                    ProgramEnrollmentStatus.ENROLLED);
            SubjectSection section = fixtures.section(fixtures.subject("Materia atomica"), fixtures.openTerm(),
                    fixtures.scheme(1, 0, true));
            SubjectSectionEnrollment sectionEnrollment = fixtures.sectionEnrollment(student, programEnrollment, section,
                    EnrollmentStatus.IN_PROGRESS);
            Long personId = student.getPerson().getId();
            Long regularId = rows.validRegular(student.getId(), programEnrollment.getId(), personId);
            Long approvedId = rows.validApprovedSubjects(student.getId(), programEnrollment.getId(), personId);
            return new Committed(programEnrollment.getId(), sectionEnrollment.getId(), regularId, approvedId);
        });
    }

    @Test
    void aFailingRevocationRollsBackTheWholeProgramWithdrawal() {
        Committed committed = commitEnrollmentWithCertificates();
        doThrow(new IllegalStateException("revocation failed")).when(approvedSubjectsRevoker).revokeValidCertificates(any(), any());

        assertThatThrownBy(() -> programEnrollmentService.withdrawForNonpayment(committed.programEnrollmentId()))
                .isInstanceOf(IllegalStateException.class);

        transactionTemplate.executeWithoutResult(status -> {
            IssuedCertificateRows rows = new IssuedCertificateRows(entityManager);
            assertThat(entityManager.find(ProgramEnrollment.class, committed.programEnrollmentId()).getStatus())
                    .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
            assertThat(entityManager.find(SubjectSectionEnrollment.class, committed.sectionEnrollmentId()).getStatus())
                    .isEqualTo(EnrollmentStatus.IN_PROGRESS);
            assertThat(rows.regularStatus(committed.regularId())).isEqualTo("VALID");
            assertThat(rows.approvedSubjectsStatus(committed.approvedId())).isEqualTo("VALID");
        });
    }
}
