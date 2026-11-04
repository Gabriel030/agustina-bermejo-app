package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Term;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomSlotOccupancyAssemblerTest {

    private final ClassroomSlotOccupancyAssembler assembler = new ClassroomSlotOccupancyAssembler();

    private Term term(long id, LocalDateTime start, LocalDateTime end) {
        Term term = new Term();
        term.setId(id);
        term.setDescription("Term " + id);
        term.setStartDate(start);
        term.setEndDate(end);
        return term;
    }

    private SectionScheduleSlot courseSlot(Term term) {
        Course course = new Course();
        course.setName("Excel");
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setId(9L);
        section.setName("Comision A");
        section.setTerm(term);
        section.setCourseSubject(courseSubject);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setCourseSection(section);
        slot.setDayOfWeek(DayOfWeek.MONDAY);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        return slot;
    }

    @Test
    void courseSlotIsReportedWithCourseKindAndCourseName() {
        Term term = term(1L, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 12, 15, 0, 0));

        var response = assembler.assemble(courseSlot(term), term);

        assertThat(response.section().kind()).isEqualTo(ClassroomSlotOccupancyResponse.SectionKind.COURSE);
        assertThat(response.section().name()).isEqualTo("Excel");
        assertThat(response.section().sectionName()).isEqualTo("Comision A");
        assertThat(response.section().code()).isNull();
    }

    @Test
    void termEndingTheDayBeforeTheReferenceStartsDoesNotOverlap() {
        Term earlier = term(1L, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 6, 30, 0, 0));
        Term reference = term(2L, LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 12, 15, 0, 0));

        assertThat(assembler.assemble(courseSlot(earlier), reference).overlapsInDates()).isFalse();
    }

    @Test
    void termsSharingTheBoundaryInstantOverlap() {
        Term earlier = term(1L, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        Term reference = term(2L, LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 12, 15, 0, 0));

        assertThat(assembler.assemble(courseSlot(earlier), reference).overlapsInDates()).isTrue();
    }
}
