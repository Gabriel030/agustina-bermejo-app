package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.service.ProgramYearBillingTemplateService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.billing.configuration.service.LegacyConfigurationMirror;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateProgramCohortService implements CreateProgramCohortUseCase {

    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateService templateService;
    private final CurrentPersonService currentPersonService;
    private final LegacyConfigurationMirror configurationMirror;

    public CreateProgramCohortService(ProgramCohortRepository programCohortRepository, ProgramYearBillingTemplateService templateService,
                                       CurrentPersonService currentPersonService, LegacyConfigurationMirror configurationMirror) {
        this.configurationMirror = configurationMirror;
        this.programCohortRepository = programCohortRepository;
        this.templateService = templateService;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public ProgramCohort create(Long templateId, CreateProgramCohortRequest request) {
        ProgramYearBillingTemplate template = templateService.findOrThrow(templateId);
        Person actor = currentPersonService.resolveCurrentPerson();

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(request.intake());
        cohort.setAdmissionStart(request.admissionStart());
        cohort.setAdmissionEnd(request.admissionEnd());
        cohort.setCursadaStart(request.cursadaStart());
        cohort.setEnrollmentFeeMonth(request.enrollmentFeeMonth());
        if (request.entryFeeDiscountPercent() != null) {
            cohort.setEntryFeeDiscount(request.entryFeeDiscountPercent());
        }
        cohort.setCreatedBy(actor);
        ProgramCohort saved = programCohortRepository.save(cohort);
        configurationMirror.recordCohortEntryDiscount(saved);
        return saved;
    }
}
