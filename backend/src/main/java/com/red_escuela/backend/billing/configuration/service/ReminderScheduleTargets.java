package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.api.TeachingTargetChecker;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.exception.ReminderScheduleTargetNotFoundException;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.policy.ReminderScheduleChangeCheck;
import com.red_escuela.backend.billing.configuration.policy.ReminderScheduleCheck;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduleTargets {
    private final TeachingTargetChecker teachingTargetChecker;
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public ReminderScheduleTargets(TeachingTargetChecker teachingTargetChecker, ConfigurationPolicyEvaluator policyEvaluator) {
        this.teachingTargetChecker = teachingTargetChecker;
        this.policyEvaluator = policyEvaluator;
    }

    public ConfigurationTopic requireExistingTopic(ReminderScheduleTarget target) {
        if (target.targetId() == null) {
            return ConfigurationTopic.ofReminderScheduleGroup(target.group());
        }
        boolean exists = switch (target.group()) {
            case PROGRAMS -> teachingTargetChecker.programExists(target.targetId());
            case MONTHLY_COURSES -> teachingTargetChecker.courseExists(target.targetId());
        };
        if (!exists) {
            throw new ReminderScheduleTargetNotFoundException(target.targetId());
        }
        return ConfigurationTopic.ofReminderScheduleException(target.group(), target.targetId());
    }

    public void requireValidProposal(ReminderScheduleProposal proposal) {
        ConfigurationTopic topic = requireExistingTopic(proposal.target());
        policyEvaluator.requireReminderScheduleChangeNotToFollowGroup(new ReminderScheduleChangeCheck(proposal.schedule()));
        policyEvaluator.requireCoherentCollectionReminderSchedule(new ReminderScheduleCheck(proposal.schedule(), topic.isReminderScheduleException()));
        proposal.proposedGlobalSettings().ifPresent(policyEvaluator::requireCoherentCollectionReminderSettings);
    }

    public void requireValidGlobalSettings(CollectionReminderSettings settings) {
        policyEvaluator.requireCoherentCollectionReminderSettings(settings);
    }

    public ConfigurationValues requestedValuesOf(TeachingGroup group, ReminderSchedule schedule) {
        policyEvaluator.requireReminderScheduleChangeNotToFollowGroup(new ReminderScheduleChangeCheck(schedule));
        return valuesOf(group, schedule);
    }

    public ConfigurationValues valuesOf(TeachingGroup group, ReminderSchedule schedule) {
        return switch (group) {
            case PROGRAMS -> new ProgramReminderSchedule(schedule);
            case MONTHLY_COURSES -> new CourseReminderSchedule(schedule);
        };
    }
}
