package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateExceedsTermSpanException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.TermRepository;
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

/**
 * Decisión 2026-09-03: un pago nunca puede cubrir más de un cohorte (ej. no adelantar un año
 * entero de una carrera cuatrimestral) — RecurringFeeWindowService ya lo garantiza en runtime
 * acotando contra recurringFeeInvoicesCount de la plantilla, pero eso solo sirve si esa cifra refleja
 * la duración real del Term. Esto blinda esa precondición en el alta de la plantilla.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProgramYearBillingTemplateServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramYearBillingTemplateService templateService;

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
        Term term = persistTerm(base, base.plusMonths(3)); // 4 meses exactos (cuatrimestre)

        assertThatThrownBy(() -> templateService.crear(program.getId(), term.getId(), 5))
                .isInstanceOf(ProgramYearBillingTemplateExceedsTermSpanException.class);
    }

    @Test
    void recurringFeeInvoicesCountEqualToTermMonthsIsAccepted() {
        Person director = persistPerson("director.exacto." + System.nanoTime());
        authenticateAs(director);
        Program program = persistProgram("Carrera cuatrimestral exacta " + System.nanoTime());
        YearMonth base = YearMonth.now();
        Term term = persistTerm(base, base.plusMonths(3)); // 4 meses exactos

        var template = templateService.crear(program.getId(), term.getId(), 4);

        assertThat(template.getRecurringFeeInvoicesCount()).isEqualTo(4);
    }

    // Term sin startDate/endDate configurados (fixtures viejas/incompletas): no hay ventana
    // real contra la cual validar, así que no bloquea — mismo criterio de degradación que
    // RecurringFeeWindowService.
    @Test
    void withoutTermDatesNothingIsValidated() {
        Person director = persistPerson("director.sinfechas." + System.nanoTime());
        authenticateAs(director);
        Program program = persistProgram("Carrera sin fechas " + System.nanoTime());
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        var template = templateService.crear(program.getId(), term.getId(), 999);

        assertThat(template.getRecurringFeeInvoicesCount()).isEqualTo(999);
    }
}
