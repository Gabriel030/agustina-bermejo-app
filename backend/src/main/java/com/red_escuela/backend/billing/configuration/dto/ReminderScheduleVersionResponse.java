package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;

import java.time.LocalDate;

public record ReminderScheduleVersionResponse(Long id, ConfigurationVersionStatus status, LocalDate effectiveFrom, String changeReason, boolean directPublication,
                                              Long proposedById, Long decidedById, String decisionReason, ReminderScheduleDto schedule) {

    public static ReminderScheduleVersionResponse from(ConfigurationVersion version) {
        return new ReminderScheduleVersionResponse(version.getId(), version.getStatus(), version.getEffectiveFrom(), version.getChangeReason(),
                version.isDirectPublication(), version.getProposedById(), version.getDecidedById(), version.getDecisionReason(),
                ReminderScheduleDto.from(scheduleOf(version.values())));
    }

    private static ReminderSchedule scheduleOf(ConfigurationValues values) {
        return switch (values) {
            case ProgramReminderSchedule program -> program.schedule();
            case CourseReminderSchedule course -> course.schedule();
            default -> throw new IllegalStateException("La versión no es una lista de avisos: " + values.scope());
        };
    }
}
