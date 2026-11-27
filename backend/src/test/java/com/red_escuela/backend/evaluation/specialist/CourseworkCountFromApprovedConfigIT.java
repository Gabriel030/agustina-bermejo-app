package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.GradeGridColumnResponse;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.GradeItemOutsideConfiguredSlotsException;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import com.red_escuela.backend.testsupport.ApprovedSectionConfigFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseworkCountFromApprovedConfigIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private TeacherGradeGridBuilder teacherGradeGridBuilder;

    @Autowired
    private ClosureColumnsFinder closureColumnsFinder;

    @Autowired
    private ApprovedCourseworkCountFinder courseworkCountFinder;

    private AcademicReproductionFixtures fixtures;

    private record Setup(Instructor instructor, SubjectSection section, SubjectSectionEnrollment enrollment) {
    }

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private Setup setup(String dni) {
        Instructor instructor = fixtures.instructor(dni);
        SubjectSection section = fixtures.section(fixtures.subject("Materia" + dni), fixtures.openTerm(), fixtures.scheme(2, 0, false));
        fixtures.assignInstructor(section, instructor);
        var student = fixtures.student(dni + "1");
        var programEnrollment = fixtures.programEnrollment(student, fixtures.program("Carrera" + dni), ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = fixtures.sectionEnrollment(student, programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();
        return new Setup(instructor, section, enrollment);
    }

    private void loadItem(Setup setup, GradeItemType type, int sequence) {
        gradeItemRecorder.loadProgramEnrollmentGrade(setup.enrollment().getId(), setup.instructor().getId(),
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null, null));
    }

    @Test
    void approvedConfigDefinesTheCourseworkCountEvenWhenTheSchemeCarriesAnother() {
        Setup setup = setup("75000001");
        ApprovedSectionConfigFixture.persist(entityManager, setup.section(), 2);
        entityManager.flush();

        assertThat(courseworkCountFinder.findExpectedCount(SectionKind.PROGRAM, setup.section().getId())).isEqualTo(2);
    }

    @Test
    void teacherGridShowsTheCourseworkColumnsOfTheApprovedConfig() {
        Setup setup = setup("75000003");
        ApprovedSectionConfigFixture.persist(entityManager, setup.section(), 2);
        entityManager.flush();

        List<GradeGridColumnResponse> columns = teacherGradeGridBuilder
                .buildForProgramSection(setup.section().getId(), setup.instructor().getId()).columns();

        assertThat(columns).filteredOn(column -> column.type() == GradeItemType.TP).hasSize(2);
    }

    @Test
    void closureColumnsShowTheCourseworkColumnsOfTheApprovedConfig() {
        Setup setup = setup("75000005");
        ApprovedSectionConfigFixture.persist(entityManager, setup.section(), 2);
        entityManager.flush();

        List<ClosureSheetColumnResponse> columns = closureColumnsFinder.findColumns(SectionKind.PROGRAM, setup.section().getId());

        assertThat(columns).filteredOn(column -> column.type() == GradeItemType.TP).hasSize(2);
    }

    @Test
    void sectionWithoutApprovedConfigExpectsNoCourseworkColumns() {
        Setup setup = setup("75000007");
        SectionEvaluationConfig draft = ApprovedSectionConfigFixture.persist(entityManager, setup.section(), 3);
        draft.setStatus(SectionEvaluationConfigStatus.DRAFT);
        entityManager.flush();

        assertThat(closureColumnsFinder.findColumns(SectionKind.PROGRAM, setup.section().getId()))
                .noneMatch(column -> column.type() == GradeItemType.TP);
        assertThat(teacherGradeGridBuilder.buildForProgramSection(setup.section().getId(), setup.instructor().getId()).columns())
                .noneMatch(column -> column.type() == GradeItemType.TP);
    }

    @Test
    void courseworkWithinTheApprovedCountIsAcceptedAndBeyondItIsRejected() {
        Setup setup = setup("75000009");
        ApprovedSectionConfigFixture.persist(entityManager, setup.section(), 2);
        entityManager.flush();

        assertThatCode(() -> loadItem(setup, GradeItemType.TP, 2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> loadItem(setup, GradeItemType.TP, 3))
                .isInstanceOf(GradeItemOutsideConfiguredSlotsException.class);
        assertThatThrownBy(() -> loadItem(setup, GradeItemType.TP, 0))
                .isInstanceOf(GradeItemOutsideConfiguredSlotsException.class);
    }

    @Test
    void examWithinTheSchemeCountIsAcceptedAndBeyondItIsRejected() {
        Setup setup = setup("75000011");

        assertThatCode(() -> loadItem(setup, GradeItemType.EXAM, 2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> loadItem(setup, GradeItemType.EXAM, 3))
                .isInstanceOf(GradeItemOutsideConfiguredSlotsException.class);
    }
}
