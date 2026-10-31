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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeItemFinderTest {

    @Mock
    private GradeItemRepository gradeItemRepository;

    @InjectMocks
    private GradeItemFinder finder;

    @Test
    void mapsProgramGradeItemsToViewsKeepingGradeAndEnrollment() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(4L);
        GradeItem item = itemOf(9L, enrollment, GradeItemType.EXAM, 2);
        item.setScore(Grade.of(new BigDecimal("7.5")));
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(item));

        List<GradeItemView> views = finder.findByProgramEnrollment(4L);

        assertThat(views).containsExactly(new GradeItemView(
                9L, 4L, "EXAM", 2, Grade.of(new BigDecimal("7.5")), null, null));
    }

    @Test
    void mapsCourseGradeItemsToViewsWithTheCourseEnrollmentId() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(5L);
        GradeItem item = new GradeItem();
        item.setId(11L);
        item.setCourseSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(1);
        when(gradeItemRepository.findByCourseSubjectSectionEnrollmentId(5L)).thenReturn(List.of(item));

        assertThat(finder.findByCourseEnrollment(5L)).containsExactly(new GradeItemView(
                11L, 5L, "EXAM", 1, null, null, null));
    }

    @Test
    void keepsAnUnscoredItemWithoutGrade() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(4L);
        GradeItem item = itemOf(10L, enrollment, GradeItemType.TP, 1);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(item));

        assertThat(finder.findByProgramEnrollment(4L)).singleElement().extracting(GradeItemView::score).isNull();
    }

    private static GradeItem itemOf(Long id, SubjectSectionEnrollment enrollment, GradeItemType type, Integer sequence) {
        GradeItem item = new GradeItem();
        item.setId(id);
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(type);
        item.setSequenceNumber(sequence);
        return item;
    }
}
