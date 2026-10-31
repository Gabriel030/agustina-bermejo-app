package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrollmentGradeItemsFinderTest {

    private final GradeItemRepository gradeItemRepository = mock(GradeItemRepository.class);
    private final EnrollmentGradeItemsFinder finder = new EnrollmentGradeItemsFinder(gradeItemRepository);

    @Test
    void mapsProgramGradeItemsToViewsKeepingScoreAndSequence() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(4L);
        GradeItem scored = gradeItem(enrollment, 1L, GradeItemType.EXAM, 2, Grade.of(new BigDecimal("7.50")));
        GradeItem unscored = gradeItem(enrollment, 2L, GradeItemType.TP, 1, null);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(scored, unscored));

        List<EnrollmentGradeItemView> views = finder.findByProgramEnrollment(4L);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).enrollmentId()).isEqualTo(4L);
        assertThat(views.get(0).type()).isEqualTo("EXAM");
        assertThat(views.get(0).sequenceNumber()).isEqualTo(2);
        assertThat(views.get(0).score()).isEqualTo(Grade.of(new BigDecimal("7.50")));
        assertThat(views.get(0).examBoardId()).isNull();
        assertThat(views.get(1).score()).isNull();
    }

    private GradeItem gradeItem(SubjectSectionEnrollment enrollment, Long id, GradeItemType type, Integer sequence, Grade score) {
        GradeItem item = new GradeItem();
        item.setId(id);
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(type);
        item.setSequenceNumber(sequence);
        item.setScore(score);
        return item;
    }
}
