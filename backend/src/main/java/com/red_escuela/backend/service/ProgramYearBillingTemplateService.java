package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramYearBillingTemplateService {

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramRepository programRepository;
    private final TermRepository termRepository;
    private final CurrentPersonService currentPersonService;

    public ProgramYearBillingTemplateService(ProgramYearBillingTemplateRepository templateRepository, ProgramRepository programRepository,
                                              TermRepository termRepository, CurrentPersonService currentPersonService) {
        this.templateRepository = templateRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.currentPersonService = currentPersonService;
    }

    // La validación de recurringFeeInvoicesCount contra la duración del Term (decisión 2026-09-03: un
    // pago nunca puede cubrir más de un cohorte) vive en ProgramYearBillingTemplate misma
    // (@PrePersist) — se autovalida al persistirse, sin importar qué código la construya.
    @Transactional
    public ProgramYearBillingTemplate crear(Long programId, Long termId, Integer recurringFeeInvoicesCount) {
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

    public ProgramYearBillingTemplate findOrThrow(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(id));
    }
}
