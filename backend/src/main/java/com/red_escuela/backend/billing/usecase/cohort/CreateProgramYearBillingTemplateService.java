package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.TermRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

// La validación de recurringFeeInvoicesCount contra la duración del Term (decisión 2026-09-03: un
// pago nunca puede cubrir más de un cohorte) vive en ProgramYearBillingTemplate misma
// (@PrePersist) — se autovalida al persistirse, sin importar qué código la construya.
@UseCase
public class CreateProgramYearBillingTemplateService implements CreateProgramYearBillingTemplateUseCase {

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramRepository programRepository;
    private final TermRepository termRepository;
    private final CurrentPersonService currentPersonService;

    public CreateProgramYearBillingTemplateService(ProgramYearBillingTemplateRepository templateRepository, ProgramRepository programRepository,
                                                     TermRepository termRepository, CurrentPersonService currentPersonService) {
        this.templateRepository = templateRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public ProgramYearBillingTemplate create(Long programId, Long termId, Integer recurringFeeInvoicesCount) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        Term term = termRepository.findById(termId).orElseThrow(() -> new TermNotFoundException(termId));
        Person actor = currentPersonService.resolveCurrentPerson();

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(recurringFeeInvoicesCount);
        template.setCreatedBy(actor);
        return templateRepository.save(template);
    }
}
