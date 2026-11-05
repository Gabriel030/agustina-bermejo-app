package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.HolidayFinder;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOnHolidayException;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOutsideTermException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.stream.Stream;

@Service
public class SectionEvaluationConfigDateValidator {

    private final HolidayFinder holidayFinder;

    public SectionEvaluationConfigDateValidator(HolidayFinder holidayFinder) {
        this.holidayFinder = holidayFinder;
    }

    public void requireDatesWithinTermAndOffHolidays(SectionEvaluationConfig config, UpdateSectionEvaluationConfigRequest request) {
        Term term = config.getSubjectSection().getTerm();
        Stream.of(request.tp1DueDate(), request.tp2DueDate(), request.tp3DueDate(),
                        request.exam1Date(), request.exam2Date(), request.exam3Date(), request.exam4Date(),
                        request.finalCall1Date(), request.finalCall2Date())
                .forEach(date -> requireDateAllowed(term, date));
    }

    private void requireDateAllowed(Term term, LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate start = term.getStartDate() != null ? term.getStartDate().toLocalDate() : null;
        LocalDate end = term.getEndDate() != null ? term.getEndDate().toLocalDate() : null;
        if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) {
            throw new EvaluationDateOutsideTermException(date);
        }
        if (holidayFinder.isHoliday(date)) {
            throw new EvaluationDateOnHolidayException(date);
        }
    }
}
