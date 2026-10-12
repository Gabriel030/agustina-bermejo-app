package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.configuration.exception.InvalidYearBillingConfigurationException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class ProgramYearBillingTemplateServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase createTemplateUseCase;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AddressRepository addressRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Program persistProgram(String nombre) {
        Program program = new Program();
        program.setName(nombre);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private Term persistTerm(YearMonth first, YearMonth last) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(first.atDay(1).atStartOfDay());
        term.setEndDate(last.atEndOfMonth().atTime(23, 59));
        return termRepository.save(term);
    }

    @Test
    void recurringFeeInvoicesCountAboveTermMonthsIsRejected() {
        Person director = persistPerson("director.tope." + System.nanoTime());
        authenticateAs(director);
        Program program = persistProgram("Carrera cuatrimestral " + System.nanoTime());
        YearMonth base = YearMonth.now();
        Term term = persistTerm(base, base.plusMonths(3));

        assertThatThrownBy(() -> createTemplateUseCase.create(program.getId(), term.getId(), 5))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).containsExactly("INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN"));
    }

    @Test
    void recurringFeeInvoicesCountEqualToTermMonthsIsAccepted() {
        Person director = persistPerson("director.exacto." + System.nanoTime());
        authenticateAs(director);
        Program program = persistProgram("Carrera cuatrimestral exacta " + System.nanoTime());
        YearMonth base = YearMonth.now();
        Term term = persistTerm(base, base.plusMonths(3));

        var template = createTemplateUseCase.create(program.getId(), term.getId(), 4);

        assertThat(com.red_escuela.backend.billing.planning.TestTemplateTerms.latest(template).recurringFeeInvoicesCount()).isEqualTo(4);
    }

    @Test
    @org.junit.jupiter.api.Tag("D-002")
    void withoutTermDatesTheTemplateIsRejected() {
        Person director = persistPerson("director.sinfechas." + System.nanoTime());
        authenticateAs(director);
        Program program = persistProgram("Carrera sin fechas " + System.nanoTime());
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Long termId = term.getId();

        assertThatThrownBy(() -> createTemplateUseCase.create(program.getId(), termId, 999))
                .isInstanceOfSatisfying(InvalidYearBillingConfigurationException.class,
                        e -> assertThat(e.violations()).extracting(PolicyViolation::code).contains("TERM_WITHOUT_DATES"));
    }
}
