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

import com.red_escuela.backend.shared.domain.InvalidPercentageException;
import com.red_escuela.backend.shared.domain.Percentage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollectionsPolicyServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.collectionspolicy.CreateCollectionsPolicyUseCase createCollectionsPolicyUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.collectionspolicy.FindCurrentCollectionsPolicyUseCase findCurrentCollectionsPolicyUseCase;

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
        CollectionsPolicy today = createCollectionsPolicyUseCase.create(new CreateCollectionsPolicyRequest(
                10, Percentage.ofFraction(BigDecimal.valueOf(0.10)), 30, 2, 60, 3, LocalDate.now()));

        createCollectionsPolicyUseCase.create(new CreateCollectionsPolicyRequest(
                5, Percentage.ofFraction(BigDecimal.valueOf(0.20)), 15, 1, 30, 2, LocalDate.now().plusMonths(1)));

        CollectionsPolicy current = findCurrentCollectionsPolicyUseCase.findCurrent();

        assertThat(current.getId()).isEqualTo(today.getId());
        assertThat(current.getGracePeriodDays()).isEqualTo(10);
    }

    @Test
    void theMostRecentPastOrPresentPolicyWins() {
        Person director = persistPerson("director.policy2." + System.nanoTime());
        authenticateAs(director);
        createCollectionsPolicyUseCase.create(new CreateCollectionsPolicyRequest(
                10, Percentage.ofFraction(BigDecimal.valueOf(0.10)), 30, 2, 60, 3, LocalDate.now().minusMonths(2)));
        CollectionsPolicy mostRecent = createCollectionsPolicyUseCase.create(new CreateCollectionsPolicyRequest(
                7, Percentage.ofFraction(BigDecimal.valueOf(0.15)), 20, 1, 45, 2, LocalDate.now().minusDays(1)));

        CollectionsPolicy current = findCurrentCollectionsPolicyUseCase.findCurrent();

        assertThat(current.getId()).isEqualTo(mostRecent.getId());
        assertThat(current.getGracePeriodDays()).isEqualTo(7);
    }

    @Test
    void aSurchargePercentOutsideZeroToOneHundredIsRejected() {
        Person director = persistPerson("director.policy3." + System.nanoTime());
        authenticateAs(director);

        assertThatThrownBy(() -> new CreateCollectionsPolicyRequest(
                10, Percentage.ofPoints(BigDecimal.valueOf(150)), 30, 2, 60, 3, LocalDate.now()))
                .isInstanceOf(InvalidPercentageException.class);
        assertThatThrownBy(() -> new CreateCollectionsPolicyRequest(
                10, Percentage.ofPoints(BigDecimal.valueOf(-10)), 30, 2, 60, 3, LocalDate.now()))
                .isInstanceOf(InvalidPercentageException.class);
    }
}
