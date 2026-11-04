package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionShiftRequiredException;
import com.red_escuela.backend.academicstructure.exception.SlotOutsideShiftException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionIdentityAssignerTest {

    @Mock
    private SectionCodeGenerator codeGenerator;
    @Mock
    private SectionShiftSlotGuard shiftSlotGuard;
    @Mock
    private SectionScheduleSlotRepository slotRepository;

    private SectionIdentityAssigner assigner;
    private Term term;

    @BeforeEach
    void setUp() {
        assigner = new SectionIdentityAssigner(codeGenerator, shiftSlotGuard, slotRepository);
        term = TermFixtures.newTerm();
    }

    private static SectionScheduleSlot slot(int startHour, int endHour) {
        var slot = new SectionScheduleSlot();
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        return slot;
    }

    @Test
    void newSubjectSectionGetsShiftDescriptionAndGeneratedCode() {
        var subject = new Subject();
        subject.setCode("MAT1");
        var section = new SubjectSection();
        section.setSubject(subject);
        section.setTerm(term);
        when(codeGenerator.nextCodeForSubject("MAT1", term)).thenReturn("MAT1-2026C1-01");

        assigner.identifyNewSubjectSection(section, SectionShift.EVENING, "Grupo reducido");

        assertThat(section.getShift()).isEqualTo(SectionShift.EVENING);
        assertThat(section.getDescription()).isEqualTo("Grupo reducido");
        assertThat(section.getCode()).isEqualTo("MAT1-2026C1-01");
    }

    @Test
    void newCourseSectionGetsShiftDescriptionAndGeneratedCode() {
        var courseSubject = new CourseSubject();
        courseSubject.setId(12L);
        var section = new CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        when(codeGenerator.nextCodeForCourseSubject(12L, term)).thenReturn("CM12-2026A-03");

        assigner.identifyNewCourseSection(section, SectionShift.AFTERNOON, null);

        assertThat(section.getShift()).isEqualTo(SectionShift.AFTERNOON);
        assertThat(section.getCode()).isEqualTo("CM12-2026A-03");
    }

    @Test
    void changingTheShiftChecksEverySlotOfTheSubjectSection() {
        var section = new SubjectSection();
        section.setId(5L);
        when(slotRepository.findBySubjectSectionId(5L)).thenReturn(List.of(slot(9, 11), slot(14, 16)));
        lenient().doThrow(new SlotOutsideShiftException(SectionShift.MORNING, LocalTime.of(14, 0), LocalTime.of(16, 0), LocalTime.of(8, 0), LocalTime.of(12, 30)))
                .when(shiftSlotGuard).ensureInsideShift(eq(SectionShift.MORNING), eq(LocalTime.of(14, 0)), any());

        assertThatThrownBy(() -> assigner.changeSubjectSectionShift(section, SectionShift.MORNING))
                .isInstanceOf(SlotOutsideShiftException.class);
        assertThat(section.getShift()).isNull();
    }

    @Test
    void changingTheShiftWithAllSlotsInsideKeepsTheNewShift() {
        var section = new CourseSubjectSection();
        section.setId(6L);
        when(slotRepository.findByCourseSectionId(6L)).thenReturn(List.of(slot(9, 11)));

        assigner.changeCourseSectionShift(section, SectionShift.MORNING);

        assertThat(section.getShift()).isEqualTo(SectionShift.MORNING);
    }

    @Test
    void missingShiftIsRejectedForNewSections() {
        assertThatThrownBy(() -> assigner.requireShift(null)).isInstanceOf(SectionShiftRequiredException.class);
    }

    @Test
    void presentShiftIsAccepted() {
        assertThatCode(() -> assigner.requireShift(SectionShift.EVENING)).doesNotThrowAnyException();
    }
}
