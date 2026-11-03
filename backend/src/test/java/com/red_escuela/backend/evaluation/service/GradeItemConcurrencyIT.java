package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentRaceFixtures;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GradeItemConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private GradeItemService gradeItemService;
    @Autowired
    private GradeItemRepository gradeItemRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long enrollmentId;
    private Long instructorId;

    @BeforeEach
    void seedEnrollmentWithEvaluationScheme() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new ProgramEnrollmentRaceFixtures(entityManager);
            var program = fixtures.cycle().program();
            Term term = fixtures.term();
            Subject subject = fixtures.cycle().subject();
            SubjectSection section = fixtures.section(subject, term, fixtures.cycleOf(program, term), 20);
            EvaluationScheme scheme = new EvaluationScheme();
            scheme.setName("Esquema " + System.nanoTime());
            scheme.setExamCountExpected(2);
            scheme.setTpCountExpected(0);
            scheme.setHasFinal(false);
            scheme.setMinRegularGrade(Grade.of(new BigDecimal("4")));
            scheme.setMinApprovalGrade(Grade.of(new BigDecimal("6")));
            entityManager.persist(scheme);
            section.setEvaluationScheme(scheme);
            Instructor instructor = fixtures.cycle().instructor();
            fixtures.cycle().assignLead(section, instructor);
            ProgramEnrollment programEnrollment = fixtures.programEnrollment(program);
            SubjectSectionEnrollment enrollment = fixtures.sectionEnrollment(
                    programEnrollment, section, EnrollmentStatus.IN_PROGRESS, LocalDateTime.now());
            entityManager.flush();
            enrollmentId = enrollment.getId();
            instructorId = instructor.getId();
        });
    }

    private CreateGradeItemRequest examGrade(String score) {
        return new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), new BigDecimal(score), null, null);
    }

    @RepeatedTest(5)
    void twoTeachersLoadingTheSameGradeSlotSimultaneouslyLeaveASingleGradeItem() throws Exception {
        Callable<GradeItem> firstLoad = () -> gradeItemService.loadProgramEnrollmentGrade(enrollmentId, instructorId, examGrade("7"));
        Callable<GradeItem> secondLoad = () -> gradeItemService.loadProgramEnrollmentGrade(enrollmentId, instructorId, examGrade("9"));

        List<Outcome<GradeItem>> outcomes = ConcurrentRace.run(List.of(firstLoad, secondLoad));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId)).hasSize(1);
    }
}
