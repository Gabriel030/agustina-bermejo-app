package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ApprovedCourseworkGradeRepository;
import com.red_escuela.backend.enrollment.repository.ApprovedCourseworkGradeRepository.ApprovedCourseworkGradeRow;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovedSubjectGradesReaderTest {

    private static final Long STUDENT_ID = 3L;
    private static final Long ENROLLMENT_ID = 5L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final ProgramProgressReader progressReader = mock(ProgramProgressReader.class);
    private final ApprovedCourseworkGradeRepository courseworkRepository = mock(ApprovedCourseworkGradeRepository.class);
    private final SubjectEquivalenceRepository equivalenceRepository = mock(SubjectEquivalenceRepository.class);
    private final ApprovedSubjectGradesReader reader = new ApprovedSubjectGradesReader(
            enrollmentRepository, progressReader, courseworkRepository, equivalenceRepository);

    @BeforeEach
    void setUp() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(enrollment.getStudent()).thenReturn(student);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(progressReader.listApprovedSubjectsWithSource(ENROLLMENT_ID)).thenReturn(List.of());
        when(courseworkRepository.findByProgramEnrollmentAndStatusLatestFirst(eq(ENROLLMENT_ID), any()))
                .thenReturn(List.of());
        when(equivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(eq(ENROLLMENT_ID), any()))
                .thenReturn(List.of());
    }

    @Test
    void enrollmentWithoutApprovedSubjectsReturnsEmptyList() {
        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID)).isEmpty();
    }

    @Test
    void courseworkApprovalCarriesGradeAndApprovalDate() {
        approve(subject(1L, "Algebra", 1), ApprovalSource.COURSEWORK);
        coursework(1L, "8.50", LocalDate.of(2026, 7, 10));

        List<ApprovedSubjectGrade> result = reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID);

        assertThat(result).singleElement().satisfies(grade -> {
            assertThat(grade.subjectName()).isEqualTo("Algebra");
            assertThat(grade.curricularYear()).isEqualTo(1);
            assertThat(grade.approvalSource()).isEqualTo(ApprovalSource.COURSEWORK);
            assertThat(grade.grade().value()).isEqualByComparingTo("8.50");
            assertThat(grade.approvedOn()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(grade.hasGrade()).isTrue();
            assertThat(grade.hasApprovalDate()).isTrue();
        });
    }

    @Test
    void equivalenceWithoutOriginScoreHasNoGradeInsteadOfAZero() {
        approve(subject(2L, "Historia", 1), ApprovalSource.EQUIVALENCE);
        equivalence(2L, null, LocalDate.of(2026, 3, 1));

        List<ApprovedSubjectGrade> result = reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID);

        assertThat(result).singleElement().satisfies(grade -> {
            assertThat(grade.approvalSource()).isEqualTo(ApprovalSource.EQUIVALENCE);
            assertThat(grade.grade()).isNull();
            assertThat(grade.hasGrade()).isFalse();
            assertThat(grade.approvedOn()).isEqualTo(LocalDate.of(2026, 3, 1));
        });
    }

    @Test
    void equivalenceWithOriginScoreExposesItAsTheGrade() {
        approve(subject(2L, "Historia", 1), ApprovalSource.EQUIVALENCE);
        equivalence(2L, "7.00", LocalDate.of(2026, 3, 1));

        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID)).singleElement()
                .satisfies(grade -> assertThat(grade.grade().value()).isEqualByComparingTo("7.00"));
    }

    @Test
    void courseworkApprovalWithoutScoreKeepsNullGradeAndKnownDate() {
        approve(subject(1L, "Algebra", 1), ApprovalSource.COURSEWORK);
        coursework(1L, null, LocalDate.of(2026, 7, 10));

        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID)).singleElement().satisfies(grade -> {
            assertThat(grade.hasGrade()).isFalse();
            assertThat(grade.hasApprovalDate()).isTrue();
        });
    }

    @Test
    void resultIsOrderedByCurricularYearThenNameWithUnknownYearLast() {
        approve(subject(1L, "Zoologia", 1), ApprovalSource.COURSEWORK);
        approve(subject(2L, "algebra", 2), ApprovalSource.COURSEWORK);
        approve(subject(3L, "Biologia", 1), ApprovalSource.COURSEWORK);
        approve(subject(4L, "Taller", null), ApprovalSource.COURSEWORK);
        approve(subject(5L, "Anatomia", 2), ApprovalSource.COURSEWORK);

        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID))
                .extracting(ApprovedSubjectGrade::subjectName)
                .containsExactly("Biologia", "Zoologia", "algebra", "Anatomia", "Taller");
    }

    @Test
    void latestApprovedAttemptProvidesTheGradeWhenThereAreSeveral() {
        approve(subject(1L, "Algebra", 1), ApprovalSource.COURSEWORK);
        coursework(1L, "9.00", LocalDate.of(2026, 12, 1));
        coursework(1L, "6.00", LocalDate.of(2026, 7, 1));

        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID)).singleElement()
                .satisfies(grade -> assertThat(grade.grade().value()).isEqualByComparingTo("9.00"));
    }

    @Test
    void subjectsNotReportedAsApprovedByTheProgressRuleAreNeverReturned() {
        approve(subject(1L, "Algebra", 1), ApprovalSource.COURSEWORK);
        coursework(1L, "8.00", LocalDate.of(2026, 7, 10));
        coursework(99L, "9.00", LocalDate.of(2026, 7, 10));
        equivalence(98L, "9.00", LocalDate.of(2026, 3, 1));

        assertThat(reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID))
                .extracting(ApprovedSubjectGrade::subjectId).containsExactly(1L);
    }

    @Test
    void onlyApprovedCourseworkIsRequestedFromTheRepository() {
        approve(subject(1L, "Algebra", 1), ApprovalSource.COURSEWORK);

        reader.listApprovedSubjectGrades(STUDENT_ID, ENROLLMENT_ID);

        verify(courseworkRepository).findByProgramEnrollmentAndStatusLatestFirst(ENROLLMENT_ID, EnrollmentStatus.APPROVED);
        verify(equivalenceRepository).findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(
                ENROLLMENT_ID, SubjectEquivalenceStatus.ACTIVE);
    }

    @Test
    void anotherStudentsEnrollmentIsNotFound() {
        assertThatThrownBy(() -> reader.listApprovedSubjectGrades(STUDENT_ID + 1, ENROLLMENT_ID))
                .isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void unknownEnrollmentIsNotFound() {
        when(enrollmentRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.listApprovedSubjectGrades(STUDENT_ID, 77L))
                .isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    private static ProgramSubjectView subject(Long id, String name, Integer year) {
        return new ProgramSubjectView(id, id, name, 4, year, null, null);
    }

    private void approve(ProgramSubjectView subject, ApprovalSource source) {
        List<ApprovedSubjectView> approved = new java.util.ArrayList<>(
                progressReader.listApprovedSubjectsWithSource(ENROLLMENT_ID));
        approved.add(new ApprovedSubjectView(subject, source));
        when(progressReader.listApprovedSubjectsWithSource(ENROLLMENT_ID)).thenReturn(approved);
    }

    private void coursework(Long subjectId, String score, LocalDate date) {
        ApprovedCourseworkGradeRow row = mock(ApprovedCourseworkGradeRow.class);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getFinalScore()).thenReturn(score == null ? null : Grade.of(new BigDecimal(score)));
        when(row.getFinalScoreDate()).thenReturn(date);
        List<ApprovedCourseworkGradeRow> rows = new java.util.ArrayList<>(
                courseworkRepository.findByProgramEnrollmentAndStatusLatestFirst(ENROLLMENT_ID, EnrollmentStatus.APPROVED));
        rows.add(row);
        when(courseworkRepository.findByProgramEnrollmentAndStatusLatestFirst(eq(ENROLLMENT_ID), any())).thenReturn(rows);
    }

    private void equivalence(Long subjectId, String score, LocalDate recognizedOn) {
        SubjectEquivalence equivalence = mock(SubjectEquivalence.class);
        when(equivalence.getSubjectId()).thenReturn(subjectId);
        when(equivalence.getOriginScore()).thenReturn(score == null ? null : Grade.of(new BigDecimal(score)));
        when(equivalence.getRecognizedOn()).thenReturn(recognizedOn);
        when(equivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(eq(ENROLLMENT_ID), any()))
                .thenReturn(List.of(equivalence));
    }
}
