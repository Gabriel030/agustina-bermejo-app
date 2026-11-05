package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentGradeScoresFinderTest {

    @Mock
    private GradeItemRepository gradeItemRepository;

    @InjectMocks
    private EnrollmentGradeScoresFinder finder;

    @Test
    void groupsProgramScoresByEnrollmentAndType() {
        SubjectSectionEnrollment first = new SubjectSectionEnrollment();
        first.setId(1L);
        SubjectSectionEnrollment second = new SubjectSectionEnrollment();
        second.setId(2L);
        GradeItem exam = programItem(first, GradeItemType.EXAM, 1, "7");
        GradeItem tp = programItem(first, GradeItemType.TP, 2, "5");
        GradeItem finalItem = programItem(second, GradeItemType.FINAL, 1, "9");
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(exam, tp, finalItem));

        Map<Long, EnrollmentGradeScores> scores = finder.findScoresByProgramEnrollment(List.of(1L, 2L));

        assertThat(scores.get(1L).examScores()).containsExactly(Map.entry(1, 7.0));
        assertThat(scores.get(1L).tpScores()).containsExactly(Map.entry(2, 5.0));
        assertThat(scores.get(1L).finalScores()).isEmpty();
        assertThat(scores.get(2L).finalScores()).containsExactly(Map.entry(1, 9.0));
        assertThat(scores.get(2L).examScores()).isEmpty();
    }

    @Test
    void ignoresItemsWithoutScoreOrSequenceNumber() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(5L);
        GradeItem withoutScore = courseItem(enrollment, GradeItemType.EXAM, 1, null);
        GradeItem withoutSequence = courseItem(enrollment, GradeItemType.EXAM, null, "8");
        GradeItem valid = courseItem(enrollment, GradeItemType.EXAM, 2, "6");
        when(gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(List.of(5L)))
                .thenReturn(List.of(withoutScore, withoutSequence, valid));

        Map<Long, EnrollmentGradeScores> scores = finder.findScoresByCourseEnrollment(List.of(5L));

        assertThat(scores.get(5L).examScores()).containsExactly(Map.entry(2, 6.0));
    }

    @Test
    void returnsNothingWithoutQueryingWhenThereAreNoEnrollments() {
        assertThat(finder.findScoresByProgramEnrollment(List.of())).isEmpty();
        assertThat(finder.findScoresByCourseEnrollment(List.of())).isEmpty();

        verifyNoInteractions(gradeItemRepository);
    }

    private GradeItem programItem(SubjectSectionEnrollment enrollment, GradeItemType type, Integer sequence, String score) {
        GradeItem item = item(type, sequence, score);
        item.setProgramSubjectSectionEnrollment(enrollment);
        return item;
    }

    private GradeItem courseItem(CourseSubjectSectionEnrollment enrollment, GradeItemType type, Integer sequence, String score) {
        GradeItem item = item(type, sequence, score);
        item.setCourseSubjectSectionEnrollment(enrollment);
        return item;
    }

    private GradeItem item(GradeItemType type, Integer sequence, String score) {
        GradeItem item = new GradeItem();
        item.setType(type);
        item.setSequenceNumber(sequence);
        item.setScore(score == null ? null : Grade.of(new BigDecimal(score)));
        return item;
    }
}
