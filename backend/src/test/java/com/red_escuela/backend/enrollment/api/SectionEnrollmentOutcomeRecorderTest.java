package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionEnrollmentOutcomeRecorderTest {

    private static final Grade SEVEN = Grade.of(new BigDecimal("7"));

    @Mock
    private SubjectSectionEnrollmentRepository programEnrollmentRepository;
    @Mock
    private CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    @InjectMocks
    private SectionEnrollmentOutcomeRecorder recorder;

    @Test
    void recordProgramOutcomeStoresStatusScoreAndTypeThenSaves() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        recorder.recordProgramOutcome(1L, EnrollmentStatus.APPROVED, SEVEN, FinalGradeType.PROMOTION, false);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(SEVEN);
        assertThat(enrollment.getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
        verify(programEnrollmentRepository).save(enrollment);
    }

    @Test
    void recordProgramOutcomeKeepsPreviousFinalWhenScoreAbsentAndNoClearRequested() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setFinalScore(SEVEN);
        enrollment.setFinalGradeType(FinalGradeType.PROMOTION);
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        recorder.recordProgramOutcome(1L, EnrollmentStatus.REGULAR, null, null, false);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(enrollment.getFinalScore()).isEqualTo(SEVEN);
        assertThat(enrollment.getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
    }

    @Test
    void recordProgramOutcomeClearsFinalWhenScoreAbsentAndClearRequested() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setFinalScore(SEVEN);
        enrollment.setFinalGradeType(FinalGradeType.PROMOTION);
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        recorder.recordProgramOutcome(1L, EnrollmentStatus.REGULAR, null, null, true);

        assertThat(enrollment.getFinalScore()).isNull();
        assertThat(enrollment.getFinalGradeType()).isNull();
        verify(programEnrollmentRepository).save(enrollment);
    }

    @Test
    void recordProgramOutcomeThrowsWhenEnrollmentMissing() {
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                recorder.recordProgramOutcome(1L, EnrollmentStatus.APPROVED, SEVEN, FinalGradeType.PROMOTION, false))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void recordCourseOutcomeStoresStatusScoreAndTypeThenSaves() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        when(courseEnrollmentRepository.findById(2L)).thenReturn(Optional.of(enrollment));

        recorder.recordCourseOutcome(2L, EnrollmentStatus.APPROVED, SEVEN, FinalGradeType.PROMOTION, false);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(SEVEN);
        assertThat(enrollment.getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
        verify(courseEnrollmentRepository).save(enrollment);
    }

    @Test
    void recordCourseOutcomeKeepsPreviousFinalWhenScoreAbsentAndNoClearRequested() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setFinalScore(SEVEN);
        enrollment.setFinalGradeType(FinalGradeType.PROMOTION);
        when(courseEnrollmentRepository.findById(2L)).thenReturn(Optional.of(enrollment));

        recorder.recordCourseOutcome(2L, EnrollmentStatus.REGULAR, null, null, false);

        assertThat(enrollment.getFinalScore()).isEqualTo(SEVEN);
        assertThat(enrollment.getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
    }

    @Test
    void recordCourseOutcomeClearsFinalWhenScoreAbsentAndClearRequested() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setFinalScore(SEVEN);
        enrollment.setFinalGradeType(FinalGradeType.PROMOTION);
        when(courseEnrollmentRepository.findById(2L)).thenReturn(Optional.of(enrollment));

        recorder.recordCourseOutcome(2L, EnrollmentStatus.REGULAR, null, null, true);

        assertThat(enrollment.getFinalScore()).isNull();
        assertThat(enrollment.getFinalGradeType()).isNull();
        verify(courseEnrollmentRepository).save(enrollment);
    }

    @Test
    void recordCourseOutcomeThrowsWhenEnrollmentMissing() {
        when(courseEnrollmentRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                recorder.recordCourseOutcome(2L, EnrollmentStatus.APPROVED, SEVEN, FinalGradeType.PROMOTION, false))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }
}
