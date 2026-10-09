package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class UpdateCohortEntryFeeDiscountService implements UpdateCohortEntryFeeDiscountUseCase {
    private static final String CHANGE_REASON = "Cambio del descuento de ingreso de la cohorte";

    private final ProgramCohortRepository programCohortRepository;
    private final ProposeBillingConfigurationChangeUseCase proposeChangeUseCase;
    private final Clock clock;

    public UpdateCohortEntryFeeDiscountService(ProgramCohortRepository programCohortRepository, ProposeBillingConfigurationChangeUseCase proposeChangeUseCase, Clock clock) {
        this.programCohortRepository = programCohortRepository;
        this.proposeChangeUseCase = proposeChangeUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProgramCohort update(Long cohortId, Percentage discount) {
        ProgramCohort cohort = programCohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        proposeChangeUseCase.proposeChange(ConfigurationTopic.ofCohortEntry(cohortId), new CohortEntryDiscount(discount), LocalDate.now(clock), CHANGE_REASON)
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        return cohort;
    }
}
