package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionFinalCallCreatorTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 12, 1);

    @Mock
    private ExamBoardService examBoardService;
    @Mock
    private SectionScheduleFinder sectionScheduleFinder;

    private SectionFinalCallCreator creator;
    private SubjectSection section;

    @BeforeEach
    void setUp() {
        creator = new SectionFinalCallCreator(examBoardService, sectionScheduleFinder);
        Subject subject = new Subject();
        subject.setId(11L);
        Term term = new Term();
        term.setId(22L);
        section = new SubjectSection();
        section.setId(33L);
        section.setSubject(subject);
        section.setTerm(term);
    }

    @Test
    void usesTheSlotOfTheExamWeekdayForTheTimeAndClassroom() {
        when(sectionScheduleFinder.findProgramSectionSlots(33L)).thenReturn(List.of(
                new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), "Aula 1"),
                new SectionSlotView(DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), "Aula 2")));

        creator.createFinalCalls(config(TUESDAY, null));

        CreateExamBoardRequest request = capturedRequest(1);
        assertThat(request.examDate()).isEqualTo(TUESDAY.atTime(18, 0));
        assertThat(request.classroom()).isEqualTo("Aula 2");
        assertThat(request.subjectId()).isEqualTo(11L);
        assertThat(request.termId()).isEqualTo(22L);
        assertThat(request.registrationEnd()).isEqualTo(TUESDAY.minusDays(2).atTime(23, 59));
    }

    @Test
    void fallsBackToTheEarliestSlotOfTheWeekWhenNoSlotMatchesTheWeekday() {
        when(sectionScheduleFinder.findProgramSectionSlots(33L)).thenReturn(List.of(
                new SectionSlotView(DayOfWeek.FRIDAY, LocalTime.of(19, 0), LocalTime.of(21, 0), "Aula 5"),
                new SectionSlotView(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), "Aula 1")));

        creator.createFinalCalls(config(TUESDAY, null));

        CreateExamBoardRequest request = capturedRequest(1);
        assertThat(request.examDate()).isEqualTo(TUESDAY.atTime(8, 0));
        assertThat(request.classroom()).isEqualTo("Aula 1");
    }

    @Test
    void usesTheStartOfTheDayWithoutClassroomWhenTheSectionHasNoSlots() {
        when(sectionScheduleFinder.findProgramSectionSlots(33L)).thenReturn(List.of());

        creator.createFinalCalls(config(TUESDAY, TUESDAY.plusDays(7)));

        List<CreateExamBoardRequest> requests = capturedRequests(2);
        assertThat(requests.get(0).examDate()).isEqualTo(TUESDAY.atStartOfDay());
        assertThat(requests.get(0).classroom()).isNull();
        assertThat(requests.get(1).examDate()).isEqualTo(TUESDAY.plusDays(7).atStartOfDay());
    }

    @Test
    void createsNoExamBoardForAMissingCallDate() {
        when(sectionScheduleFinder.findProgramSectionSlots(33L)).thenReturn(List.of());

        creator.createFinalCalls(config(null, null));

        verify(examBoardService, never()).create(any());
    }

    private SectionEvaluationConfig config(LocalDate firstCall, LocalDate secondCall) {
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setFinalCall1Date(firstCall);
        config.setFinalCall2Date(secondCall);
        config.setReviewedAt(APPROVED_AT);
        return config;
    }

    private CreateExamBoardRequest capturedRequest(int expectedCount) {
        return capturedRequests(expectedCount).get(0);
    }

    private List<CreateExamBoardRequest> capturedRequests(int expectedCount) {
        ArgumentCaptor<CreateExamBoardRequest> captor = ArgumentCaptor.forClass(CreateExamBoardRequest.class);
        verify(examBoardService, times(expectedCount)).create(captor.capture());
        return captor.getAllValues();
    }
}
