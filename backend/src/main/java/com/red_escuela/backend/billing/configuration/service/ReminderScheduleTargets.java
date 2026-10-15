package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.api.TeachingTargetLookup;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.exception.ReminderScheduleTargetNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduleTargets {
    private final TeachingTargetLookup teachingTargetLookup;

    public ReminderScheduleTargets(TeachingTargetLookup teachingTargetLookup) {
        this.teachingTargetLookup = teachingTargetLookup;
    }

    public ConfigurationTopic requireExistingTopic(ReminderScheduleTarget target) {
        if (target.targetId() == null) {
            return ConfigurationTopic.ofReminderScheduleGroup(target.group());
        }
        boolean exists = switch (target.group()) {
            case PROGRAMS -> teachingTargetLookup.programExists(target.targetId());
            case MONTHLY_COURSES -> teachingTargetLookup.courseExists(target.targetId());
        };
        if (!exists) {
            throw new ReminderScheduleTargetNotFoundException(target.targetId());
        }
        return ConfigurationTopic.ofReminderScheduleException(target.group(), target.targetId());
    }

    public ConfigurationValues valuesOf(TeachingGroup group, ReminderSchedule schedule) {
        return switch (group) {
            case PROGRAMS -> new ProgramReminderSchedule(schedule);
            case MONTHLY_COURSES -> new CourseReminderSchedule(schedule);
        };
    }
}
