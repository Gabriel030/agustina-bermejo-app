package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeItemFinderTest {

    private static final Long SECTION_ID = 30L;

    @Mock
    private GradeItemRepository gradeItemRepository;

    @Mock
    private SectionGradeItemPublicationRepository publicationRepository;

    @Mock
    private GradeItemAuditRepository auditRepository;

    @InjectMocks
    private GradeItemFinder finder;

    @Test
    void mapsPublishedProgramGradeItemsToViewsKeepingGradeAndEnrollment() {
        SubjectSectionEnrollment enrollment = programEnrollment(4L);
        GradeItem item = itemOf(9L, enrollment, GradeItemType.EXAM, 2);
        item.setScore(Grade.of(new BigDecimal("7.5")));
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(item));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, SECTION_ID))
                .thenReturn(List.of(publication(SectionKind.PROGRAM, GradeItemType.EXAM, 2)));

        List<GradeItemView> views = finder.findPublishedByProgramEnrollment(4L);

        assertThat(views).containsExactly(new GradeItemView(
                9L, 4L, "EXAM", 2, Grade.of(new BigDecimal("7.5")), null, null, null));
    }

    @Test
    void studentViewHidesUnpublishedGrades() {
        SubjectSectionEnrollment enrollment = programEnrollment(4L);
        GradeItem published = itemOf(9L, enrollment, GradeItemType.EXAM, 1);
        GradeItem unpublishedSameType = itemOf(10L, enrollment, GradeItemType.EXAM, 2);
        GradeItem unpublishedOtherType = itemOf(11L, enrollment, GradeItemType.TP, 1);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L))
                .thenReturn(List.of(published, unpublishedSameType, unpublishedOtherType));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, SECTION_ID))
                .thenReturn(List.of(publication(SectionKind.PROGRAM, GradeItemType.EXAM, 1)));

        assertThat(finder.findPublishedByProgramEnrollment(4L))
                .extracting(GradeItemView::id)
                .containsExactly(9L);
    }

    @Test
    void studentViewKeepsFinalGradesWithoutPublicationRow() {
        SubjectSectionEnrollment enrollment = programEnrollment(4L);
        GradeItem finalItem = itemOf(12L, enrollment, GradeItemType.FINAL, null);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(finalItem));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, SECTION_ID)).thenReturn(List.of());

        assertThat(finder.findPublishedByProgramEnrollment(4L)).extracting(GradeItemView::id).containsExactly(12L);
    }

    @Test
    void courseStudentViewHidesUnpublishedGradesAndKeepsPublishedOnes() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(5L);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setId(SECTION_ID);
        enrollment.setCourseSubjectSection(section);
        GradeItem published = new GradeItem();
        published.setId(11L);
        published.setCourseSubjectSectionEnrollment(enrollment);
        published.setType(GradeItemType.EXAM);
        published.setSequenceNumber(1);
        GradeItem hidden = new GradeItem();
        hidden.setId(13L);
        hidden.setCourseSubjectSectionEnrollment(enrollment);
        hidden.setType(GradeItemType.TP);
        hidden.setSequenceNumber(1);
        when(gradeItemRepository.findByCourseSubjectSectionEnrollmentId(5L)).thenReturn(List.of(published, hidden));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.COURSE, SECTION_ID))
                .thenReturn(List.of(publication(SectionKind.COURSE, GradeItemType.EXAM, 1)));

        assertThat(finder.findPublishedByCourseEnrollment(5L)).containsExactly(new GradeItemView(
                11L, 5L, "EXAM", 1, null, null, null, null));
    }

    @Test
    void studentViewCarriesOnlyTheLastCorrectionOfEachItemResolvedInOneQuery() {
        SubjectSectionEnrollment enrollment = programEnrollment(4L);
        GradeItem corrected = itemOf(9L, enrollment, GradeItemType.EXAM, 1);
        GradeItem untouched = itemOf(10L, enrollment, GradeItemType.EXAM, 2);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(corrected, untouched));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, SECTION_ID)).thenReturn(List.of(
                publication(SectionKind.PROGRAM, GradeItemType.EXAM, 1), publication(SectionKind.PROGRAM, GradeItemType.EXAM, 2)));
        when(auditRepository.findByGradeItemIdInAndKindOrderByChangedAtDescIdDesc(List.of(9L, 10L), GradeItemAuditKind.CORRECTION))
                .thenReturn(List.of(
                        correction(9L, "6", "Segunda correccion", LocalDateTime.of(2026, 11, 5, 9, 0)),
                        correction(9L, "4", "Primera correccion", LocalDateTime.of(2026, 11, 3, 9, 0))));

        List<GradeItemView> views = finder.findPublishedByProgramEnrollment(4L);

        assertThat(views.get(0).correction()).isEqualTo(new GradeItemCorrectionView(
                Grade.of(new BigDecimal("6")), "Segunda correccion", LocalDateTime.of(2026, 11, 5, 9, 0)));
        assertThat(views.get(1).correction()).isNull();
        verify(auditRepository, times(1)).findByGradeItemIdInAndKindOrderByChangedAtDescIdDesc(any(), any());
    }

    @Test
    void studentViewSkipsTheCorrectionLookupWhenNothingIsPublished() {
        SubjectSectionEnrollment enrollment = programEnrollment(4L);
        GradeItem hidden = itemOf(9L, enrollment, GradeItemType.EXAM, 1);
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of(hidden));
        when(publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, SECTION_ID)).thenReturn(List.of());

        assertThat(finder.findPublishedByProgramEnrollment(4L)).isEmpty();
        verifyNoInteractions(auditRepository);
    }

    @Test
    void returnsNothingWhenTheEnrollmentHasNoGradeItems() {
        when(gradeItemRepository.findByProgramSubjectSectionEnrollmentId(4L)).thenReturn(List.of());

        assertThat(finder.findPublishedByProgramEnrollment(4L)).isEmpty();
    }

    private static SubjectSectionEnrollment programEnrollment(Long id) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        SubjectSection section = new SubjectSection();
        section.setId(SECTION_ID);
        enrollment.setSubjectSection(section);
        return enrollment;
    }

    private static GradeItemAudit correction(Long itemId, String previousScore, String reason, LocalDateTime changedAt) {
        return GradeItemAudit.builder()
                .gradeItemId(itemId)
                .previousScore(Grade.of(new BigDecimal(previousScore)))
                .reason(reason)
                .changedAt(changedAt)
                .kind(GradeItemAuditKind.CORRECTION)
                .build();
    }

    private static SectionGradeItemPublication publication(SectionKind kind, GradeItemType type, int sequence) {
        return SectionGradeItemPublication.of(kind, SECTION_ID, type, sequence, LocalDateTime.of(2026, 11, 1, 10, 0), 1L);
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
