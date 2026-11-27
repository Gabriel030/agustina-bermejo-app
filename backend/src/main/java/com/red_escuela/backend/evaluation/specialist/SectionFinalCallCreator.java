package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.evaluation.domain.SectionRef;
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

    private final ExamBoardManager examBoardService;
    private final SectionScheduleFinder sectionScheduleFinder;

    public SectionFinalCallCreator(ExamBoardManager examBoardService, SectionScheduleFinder sectionScheduleFinder) {
        this.examBoardService = examBoardService;
        this.sectionScheduleFinder = sectionScheduleFinder;
    }

    public void createFinalCalls(SectionEvaluationConfig config) {
        SectionRef section = SectionEvaluationConfigOwner.sectionRefOf(config);
        List<SectionSlotView> slots = section.isCourse()
                ? sectionScheduleFinder.findCourseSectionSlots(section.id())
                : sectionScheduleFinder.findProgramSectionSlots(section.id());
        createFinalCall(config, slots, config.getFinalCall1Date(), config.getReviewedAt());
        createFinalCall(config, slots, config.getFinalCall2Date(), config.getReviewedAt());
    }

    private void createFinalCall(SectionEvaluationConfig config, List<SectionSlotView> slots, LocalDate examDay,
                                 LocalDateTime approvedAt) {
        if (examDay == null) {
            return;
        }
        Optional<SectionSlotView> slot = slotFor(slots, examDay);
        LocalDateTime examDate = slot.map(s -> examDay.atTime(s.startTime())).orElse(examDay.atStartOfDay());
        String classroom = slot.map(SectionSlotView::classroomName).orElse(null);
        LocalDateTime registrationEnd = examDay.minusDays(REGISTRATION_CLOSES_DAYS_BEFORE_EXAM).atTime(REGISTRATION_CLOSING_TIME);
        examBoardService.create(new CreateExamBoardRequest(
                config.subjectId(), config.courseSubjectId(), config.term().getId(),
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
