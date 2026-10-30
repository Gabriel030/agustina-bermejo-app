package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class SectionFinalCallCreator {

    private static final int REGISTRATION_CLOSES_DAYS_BEFORE_EXAM = 2;
    private static final LocalTime REGISTRATION_CLOSING_TIME = LocalTime.of(23, 59);

    private final ExamBoardService examBoardService;
    private final SectionScheduleFinder sectionScheduleFinder;

    public SectionFinalCallCreator(ExamBoardService examBoardService, SectionScheduleFinder sectionScheduleFinder) {
        this.examBoardService = examBoardService;
        this.sectionScheduleFinder = sectionScheduleFinder;
    }

    public void createFinalCalls(SectionEvaluationConfig config) {
        SubjectSection section = config.getSubjectSection();
        List<SectionSlotView> slots = sectionScheduleFinder.findProgramSectionSlots(section.getId());
        createFinalCall(section, slots, config.getFinalCall1Date(), config.getReviewedAt());
        createFinalCall(section, slots, config.getFinalCall2Date(), config.getReviewedAt());
    }

    private void createFinalCall(SubjectSection section, List<SectionSlotView> slots, LocalDate examDay,
                                 LocalDateTime approvedAt) {
        if (examDay == null) {
            return;
        }
        Optional<SectionSlotView> slot = slotFor(slots, examDay);
        LocalDateTime examDate = slot.map(s -> examDay.atTime(s.startTime())).orElse(examDay.atStartOfDay());
        String classroom = slot.map(SectionSlotView::classroomName).orElse(null);
        LocalDateTime registrationEnd = examDay.minusDays(REGISTRATION_CLOSES_DAYS_BEFORE_EXAM).atTime(REGISTRATION_CLOSING_TIME);
        examBoardService.create(new CreateExamBoardRequest(
                section.getSubject().getId(), null, section.getTerm().getId(),
                examDate, approvedAt, registrationEnd, classroom));
    }

    private Optional<SectionSlotView> slotFor(List<SectionSlotView> slots, LocalDate examDay) {
        Comparator<SectionSlotView> byWeekTime = Comparator.comparing(SectionSlotView::dayOfWeek)
                .thenComparing(SectionSlotView::startTime);
        return slots.stream()
                .filter(s -> s.dayOfWeek() == examDay.getDayOfWeek())
                .min(byWeekTime)
                .or(() -> slots.stream().min(byWeekTime));
    }
}
