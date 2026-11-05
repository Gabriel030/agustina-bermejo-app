package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.announcement.dto.AudiencePreviewResponse;
import com.red_escuela.backend.announcement.dto.AudienceSegmentResponse;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.announcement.service.audience.AudienceService;
import com.red_escuela.backend.announcement.service.audience.SectionSnapshot;
import com.red_escuela.backend.announcement.service.audience.StudentSnapshot;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.api.EvaluationSchemeSummary;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudienceUseCasesTest {

    private static final Long SECTION_ID = 60L;

    @Mock
    private AudienceService audienceService;
    @Mock
    private SectionMembership sectionMembership;

    private PreviewAudienceService previewService;
    private ListAudienceSegmentsService listService;

    @BeforeEach
    void setUp() {
        previewService = new PreviewAudienceService(audienceService, sectionMembership);
        listService = new ListAudienceSegmentsService(audienceService, sectionMembership);
    }

    private static StudentSnapshot student(long id, Map<Integer, Double> exams, Map<Integer, Double> tps) {
        Person person = new Person();
        person.setName("Ana" + id);
        person.setLastname("Gomez");
        person.setFile("F-" + id);
        Student student = new Student();
        student.setId(id);
        student.setPerson(person);
        return new StudentSnapshot(student, EnrollmentStatus.ENROLLED, null, exams, tps, Map.of(), 0, 0);
    }

    private static SectionSnapshot section(int exams, int tps, StudentSnapshot... students) {
        return new SectionSnapshot(List.of(students), new EvaluationSchemeSummary(exams, tps, true, 4.0));
    }

    private static AudienceSpec spec(AudienceSegmentKey key) {
        return new AudienceSpec(key, "1,2", null, null);
    }

    private AudiencePreviewResponse preview(SectionSnapshot section, List<StudentSnapshot> matched, AudienceSpec spec) {
        when(audienceService.snapshotOf(SectionType.PROGRAM, SECTION_ID)).thenReturn(section);
        when(audienceService.resolve(section, spec)).thenReturn(matched);
        return previewService.preview(new PreviewAudienceCommand(SectionType.PROGRAM, SECTION_ID, spec));
    }

    @Test
    void previewRejectsInstructorWhoDoesNotTeachTheSection() {
        doThrow(new NotSectionInstructorException(SECTION_ID)).when(sectionMembership)
                .requireCurrentInstructorOf(SectionType.PROGRAM, SECTION_ID);

        assertThatThrownBy(() -> previewService.preview(
                new PreviewAudienceCommand(SectionType.PROGRAM, SECTION_ID, AudienceSpec.wholeSection())))
                .isInstanceOf(NotSectionInstructorException.class);
        verify(audienceService, never()).snapshotOf(any(), any());
    }

    @Test
    void previewReportsMatchedCountTotalLabelAndStudentData() {
        StudentSnapshot ana = student(1, Map.of(1, 8.0), Map.of());
        StudentSnapshot beto = student(2, Map.of(1, 3.0), Map.of());
        AudiencePreviewResponse response = preview(section(1, 0, ana, beto), List.of(beto), AudienceSpec.wholeSection());

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.totalInSection()).isEqualTo(2);
        assertThat(response.label()).isEqualTo("Toda la comisión");
        assertThat(response.students()).singleElement().satisfies(s -> {
            assertThat(s.studentId()).isEqualTo(2L);
            assertThat(s.file()).isEqualTo("F-2");
            assertThat(s.name()).isEqualTo("Ana2 Gomez");
        });
        assertThat(response.hasUngradedInstances()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AudienceSegmentKey.class, names = {"EXAM_FAILED", "EXAM_PASSED", "EXAM_MISSING"})
    void previewFlagsExamSegmentsWhenSomeoneHasFewerGradesThanExpected(AudienceSegmentKey key) {
        StudentSnapshot complete = student(1, Map.of(1, 8.0, 2, 7.0), Map.of());
        StudentSnapshot incomplete = student(2, Map.of(1, 3.0), Map.of());

        assertThat(preview(section(2, 0, complete, incomplete), List.of(), spec(key)).hasUngradedInstances()).isTrue();
    }

    @Test
    void previewDoesNotFlagExamSegmentWhenEveryoneHasAllGrades() {
        StudentSnapshot first = student(1, Map.of(1, 8.0, 2, 7.0), Map.of());
        StudentSnapshot second = student(2, Map.of(1, 3.0, 2, 9.0), Map.of());

        assertThat(preview(section(2, 0, first, second), List.of(), spec(AudienceSegmentKey.EXAM_FAILED)).hasUngradedInstances()).isFalse();
    }

    @Test
    void previewDoesNotFlagExamSegmentWhenSchemeExpectsNoExams() {
        StudentSnapshot student = student(1, Map.of(), Map.of());

        assertThat(preview(section(0, 0, student), List.of(), spec(AudienceSegmentKey.EXAM_MISSING)).hasUngradedInstances()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AudienceSegmentKey.class, names = {"TP_FAILED", "TP_MISSING", "TP_PASSED"})
    void previewFlagsTpSegmentsWhenSomeoneHasNotSubmittedAllTps(AudienceSegmentKey key) {
        StudentSnapshot complete = student(1, Map.of(), Map.of(1, 8.0, 2, 7.0));
        StudentSnapshot incomplete = student(2, Map.of(), Map.of(1, 8.0));

        assertThat(preview(section(0, 2, complete, incomplete), List.of(), spec(key)).hasUngradedInstances()).isTrue();
    }

    @Test
    void previewDoesNotFlagTpSegmentWhenEveryoneSubmittedAllTps() {
        StudentSnapshot student = student(1, Map.of(), Map.of(1, 8.0, 2, 7.0));

        assertThat(preview(section(0, 2, student), List.of(), spec(AudienceSegmentKey.TP_PASSED)).hasUngradedInstances()).isFalse();
    }

    @Test
    void previewDoesNotCheckGradesForSegmentsThatAreNotAboutExamsOrTps() {
        StudentSnapshot student = student(1, Map.of(), Map.of());

        assertThat(preview(section(2, 2, student), List.of(), spec(AudienceSegmentKey.FINAL_PASSED)).hasUngradedInstances()).isFalse();
    }

    @Test
    void listRejectsInstructorWhoDoesNotTeachTheSection() {
        doThrow(new NotSectionInstructorException(SECTION_ID)).when(sectionMembership)
                .requireCurrentInstructorOf(SectionType.COURSE, SECTION_ID);

        assertThatThrownBy(() -> listService.list(SectionType.COURSE, SECTION_ID)).isInstanceOf(NotSectionInstructorException.class);
        verify(audienceService, never()).availableKeys(any());
    }

    @Test
    void listDescribesEachSegmentWithItsCountAndInstanceRequirements() {
        SectionSnapshot section = section(3, 4, student(1, Map.of(), Map.of()));
        when(audienceService.snapshotOf(SectionType.COURSE, SECTION_ID)).thenReturn(section);
        List<AudienceSegmentKey> keys = List.of(AudienceSegmentKey.ALL, AudienceSegmentKey.EXAM_FAILED,
                AudienceSegmentKey.TP_MISSING, AudienceSegmentKey.FINAL_FAILED, AudienceSegmentKey.ABSENCES);
        when(audienceService.availableKeys(section)).thenReturn(keys);
        when(audienceService.countFor(section, AudienceSegmentKey.ALL)).thenReturn(12);
        when(audienceService.countFor(section, AudienceSegmentKey.EXAM_FAILED)).thenReturn(-1);
        when(audienceService.countFor(section, AudienceSegmentKey.TP_MISSING)).thenReturn(0);
        when(audienceService.countFor(section, AudienceSegmentKey.FINAL_FAILED)).thenReturn(2);
        when(audienceService.countFor(section, AudienceSegmentKey.ABSENCES)).thenReturn(1);

        List<AudienceSegmentResponse> segments = listService.list(SectionType.COURSE, SECTION_ID);

        assertThat(segments).extracting(AudienceSegmentResponse::key).containsExactlyElementsOf(keys);
        assertThat(segments).extracting(AudienceSegmentResponse::count).containsExactly(12, null, 0, 2, 1);
        assertThat(segments).extracting(AudienceSegmentResponse::needsInstances).containsExactly(false, true, true, true, false);
        assertThat(segments).extracting(AudienceSegmentResponse::needsThreshold).containsExactly(false, false, false, false, true);
        assertThat(segments).extracting(AudienceSegmentResponse::instanceCount).containsExactly(null, 3, 4, 2, null);
        assertThat(segments.get(0).label()).isEqualTo("Toda la comisión");
    }
}
