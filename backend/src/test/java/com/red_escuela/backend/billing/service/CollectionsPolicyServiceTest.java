package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Fase 6.2, test 70: políticaDeCobranzaConVigenciaFuturaNoAfectaElPresente.
//
// @Transactional a nivel de clase (auditoría 2026-09-04, hallazgo real): sin esto, los dos tests
// de esta clase comparten la tabla collections_policy sin rollback (mismo container Postgres para
// toda la suite) — aFutureEffectiveFromPolicyDoesNotAffectWhatIsCurrentToday crea una política con
// effectiveFrom=hoy que sobrevivía entre tests, y si corría ANTES que
// theMostRecentPastOrPresentPolicyWins (el orden de JUnit 5 no es el de declaración), esa política
// "de hoy" ganaba el ORDER BY effectiveFrom DESC por sobre la "mostRecent" que este segundo test
// espera, haciéndolo fallar de forma intermitente según el orden real de ejecución. Verificado
// corriendo la clase aislada: pasaba por casualidad de orden, no por estar realmente aislado.
// CollectionsPolicyService.create no depende de InvoiceFactory/REQUIRES_NEW, así que
// @Transactional acá es seguro (rollback normal al final de cada test).
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollectionsPolicyServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CollectionsPolicyService collectionsPolicyService;

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

    @Test
    void aFutureEffectiveFromPolicyDoesNotAffectWhatIsCurrentToday() {
        Person director = persistPerson("director.policy1." + System.nanoTime());
        authenticateAs(director);
        CollectionsPolicy today = collectionsPolicyService.create(new CreateCollectionsPolicyRequest(
                10, BigDecimal.valueOf(0.10), 30, 2, 60, 3, LocalDate.now()));

        collectionsPolicyService.create(new CreateCollectionsPolicyRequest(
                5, BigDecimal.valueOf(0.20), 15, 1, 30, 2, LocalDate.now().plusMonths(1)));

        CollectionsPolicy current = collectionsPolicyService.findCurrent();

        assertThat(current.getId()).isEqualTo(today.getId());
        assertThat(current.getGracePeriodDays()).isEqualTo(10);
    }

    @Test
    void theMostRecentPastOrPresentPolicyWins() {
        Person director = persistPerson("director.policy2." + System.nanoTime());
        authenticateAs(director);
        collectionsPolicyService.create(new CreateCollectionsPolicyRequest(
                10, BigDecimal.valueOf(0.10), 30, 2, 60, 3, LocalDate.now().minusMonths(2)));
        CollectionsPolicy mostRecent = collectionsPolicyService.create(new CreateCollectionsPolicyRequest(
                7, BigDecimal.valueOf(0.15), 20, 1, 45, 2, LocalDate.now().minusDays(1)));

        CollectionsPolicy current = collectionsPolicyService.findCurrent();

        assertThat(current.getId()).isEqualTo(mostRecent.getId());
        assertThat(current.getGracePeriodDays()).isEqualTo(7);
    }
}
