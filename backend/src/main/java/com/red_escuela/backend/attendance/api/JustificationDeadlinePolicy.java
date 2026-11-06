package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JustificationDeadlinePolicy {

    private final InstitutionAcademicSettingsReader settingsReader;
    private final BusinessDayCalendar businessDayCalendar;
    private final Clock clock;

    public JustificationDeadlinePolicy(
            InstitutionAcademicSettingsReader settingsReader, BusinessDayCalendar businessDayCalendar, Clock clock) {
        this.settingsReader = settingsReader;
        this.businessDayCalendar = businessDayCalendar;
        this.clock = clock;
    }

    public LocalDate deadlineOf(LocalDate classDate) {
        return businessDayCalendar.nthBusinessDayAfter(
                classDate, settingsReader.read().justificationDeadlineBusinessDays());
    }

    public Map<LocalDate, LocalDate> deadlinesOf(Collection<LocalDate> classDates) {
        int businessDays = settingsReader.read().justificationDeadlineBusinessDays();
        return classDates.stream().distinct().collect(Collectors.toMap(
                Function.identity(), classDate -> businessDayCalendar.nthBusinessDayAfter(classDate, businessDays)));
    }

    public boolean isWithinDeadline(LocalDate classDate) {
        return isDeadlineOpen(deadlineOf(classDate));
    }

    public boolean isDeadlineOpen(LocalDate deadline) {
        return !LocalDate.now(clock).isAfter(deadline);
    }
}
