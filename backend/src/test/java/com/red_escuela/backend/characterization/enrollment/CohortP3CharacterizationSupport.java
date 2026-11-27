package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

abstract class CohortP3CharacterizationSupport extends WithdrawalP2CharacterizationSupport {

    protected final LocalDate today = LocalDate.now();

    @Autowired
    protected TermRepository termRepository;
    @Autowired
    protected ProgramYearBillingTemplateRepository templateRepository;
    @Autowired
    protected ProgramCohortRepository programCohortRepository;
    @Autowired
    protected ConfigurationVersionRepository configurationVersionRepository;

    protected String bearerWithRole(String roleName) {
        Person person = persistPerson("cohort.p3." + roleName.toLowerCase());
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        personRepository.save(person);
        entityManager.flush();
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    protected ProgramYearBillingTemplate persistTemplate(Program program, int year) {
        Term term = TermFixtures.newTerm();
        term.setYear(year);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(year, 1, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
        termRepository.save(term);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(persistPerson("cohort.p3.creator"));
        return TestTemplateTerms.publish(configurationVersionRepository, templateRepository.save(template));
    }

    protected ProgramCohort persistCohort(ProgramYearBillingTemplate template, LocalDate admissionStart, LocalDate admissionEnd) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplateId(template.getId());
        cohort.setProgramId(template.programId());
        cohort.setTermId(template.termId());
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(admissionStart);
        cohort.setAdmissionEnd(admissionEnd);
        cohort.setCursadaStart(admissionEnd.plusDays(15));
        cohort.setCreatedBy(persistPerson("cohort.p3.creator"));
        return programCohortRepository.save(cohort);
    }

    protected ProgramCohort persistOpenCohortOf(Program program) {
        return persistCohort(persistTemplate(program, today.getYear()), today.minusDays(10), today.plusDays(10));
    }

    protected Long storedCohortIdOf(Long programEnrollmentId) {
        entityManager.flush();
        return jdbc.queryForObject("select program_cohort_id from program_enrollment where id = ?", Long.class, programEnrollmentId);
    }

    protected Long storedTemplateIdOf(Long programEnrollmentId) {
        entityManager.flush();
        return jdbc.queryForObject("select current_billing_template_id from program_enrollment where id = ?", Long.class, programEnrollmentId);
    }
}
