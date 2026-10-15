package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.ConfigurationChangeValidator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.List;

@UseCase
public class ValidateBillingConfigurationChangeService implements ValidateBillingConfigurationChangeUseCase {
    private final ConfigurationChangeValidator validator;
    private final BillingActorResolver actorResolver;

    public ValidateBillingConfigurationChangeService(ConfigurationChangeValidator validator, BillingActorResolver actorResolver) {
        this.validator = validator;
        this.actorResolver = actorResolver;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PolicyViolation> validateChange(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason) {
        return validator.collectViolations(new ChangeBillingConfigurationCommand(topic, values, effectiveFrom, changeReason,
                actorResolver.currentPersonId(), actorResolver.currentActorIsDirector()));
    }
}
