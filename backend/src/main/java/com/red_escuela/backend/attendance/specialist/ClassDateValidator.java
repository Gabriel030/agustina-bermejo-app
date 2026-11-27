package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.exception.ClassDateAfterTermEndException;
import com.red_escuela.backend.attendance.exception.ClassDateBeforeTermStartException;
import com.red_escuela.backend.attendance.exception.ClassDateInFutureException;
import com.red_escuela.backend.attendance.exception.ClassDayWithoutScheduleSlotException;
import com.red_escuela.backend.attendance.exception.ClassOnWeekendException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
public class ClassDateValidator {

    private final SectionTermEndFinder termFinder;
    private final SectionScheduleFinder scheduleFinder;
    private final Clock clock;

    public ClassDateValidator(SectionTermEndFinder termFinder, SectionScheduleFinder scheduleFinder, Clock clock) {
        this.termFinder = termFinder;
        this.scheduleFinder = scheduleFinder;
        this.clock = clock;
    }

    public void requireValidProgramClassDate(Long sectionId, LocalDate classDate) {
        requireValidClassDate(SectionKind.PROGRAM, sectionId, classDate, scheduleFinder.findProgramSectionSlots(sectionId));
    }

    public void requireValidCourseClassDate(Long sectionId, LocalDate classDate) {
        requireValidClassDate(SectionKind.COURSE, sectionId, classDate, scheduleFinder.findCourseSectionSlots(sectionId));
    }

    private void requireValidClassDate(SectionKind kind, Long sectionId, LocalDate classDate, List<SectionSlotView> slots) {
        if (classDate.isAfter(LocalDate.now(clock))) {
            throw new ClassDateInFutureException(classDate);
        }
        if (classDate.isBefore(termFinder.termStartDate(kind, sectionId))) {
            throw new ClassDateBeforeTermStartException(classDate);
        }
        if (classDate.isAfter(termFinder.termEndDate(kind, sectionId))) {
            throw new ClassDateAfterTermEndException(classDate);
        }
        boolean hasSlotThatDay = slots.stream().anyMatch(slot -> slot.dayOfWeek() == classDate.getDayOfWeek());
        if (hasSlotThatDay) {
            return;
        }
        if (isWeekend(classDate.getDayOfWeek())) {
            throw new ClassOnWeekendException(classDate);
        }
        if (!slots.isEmpty()) {
            throw new ClassDayWithoutScheduleSlotException(classDate);
        }
    }

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
