package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.repository.AddressRepository;
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
class CreateLatePaymentSettingsServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository latePaymentSettingsRepository;

    @Autowired
    private com.red_escuela.backend.billing.usecase.latepaymentsettings.CreateLatePaymentSettingsUseCase createLatePaymentSettingsUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.latepaymentsettings.FindCurrentLatePaymentSettingsUseCase findCurrentLatePaymentSettingsUseCase;

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

    private ConfigurationVersion historicPolicy(Person createdBy, int grace, double surcharge, int suspensionDays, int invoiceCount, LocalDate effectiveFrom) {
        return LatePaymentTestFixtures.persistLatePayment(latePaymentSettingsRepository, createdBy, grace, BigDecimal.valueOf(surcharge), suspensionDays, invoiceCount, effectiveFrom);
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    @Test
    void aFutureEffectiveFromPolicyDoesNotAffectWhatIsCurrentToday() {
        Person director = persistPerson("director.policy1." + System.nanoTime());
        authenticateAs(director);
        ConfigurationVersion today = createLatePaymentSettingsUseCase.create(new CreateLatePaymentSettingsRequest(
                10, Percentage.ofFraction(BigDecimal.valueOf(0.002)), 30, 2, LocalDate.now()));

        createLatePaymentSettingsUseCase.create(new CreateLatePaymentSettingsRequest(
                5, Percentage.ofFraction(BigDecimal.valueOf(0.003)), 15, 1, LocalDate.now().plusMonths(1)));

        ConfigurationVersion current = findCurrentLatePaymentSettingsUseCase.findCurrent();

        assertThat(current.getId()).isEqualTo(today.getId());
        assertThat(((LatePaymentTerms) current.values()).gracePeriodDays()).isEqualTo(10);
    }

    @Test
    void theMostRecentPastOrPresentPolicyWins() {
        Person director = persistPerson("director.policy2." + System.nanoTime());
        authenticateAs(director);
        historicPolicy(director, 10, 0.10, 30, 2, LocalDate.now().minusMonths(2));
        ConfigurationVersion mostRecent = historicPolicy(director, 7, 0.15, 20, 1, LocalDate.now().minusDays(1));

        ConfigurationVersion current = findCurrentLatePaymentSettingsUseCase.findCurrent();

        assertThat(current.getId()).isEqualTo(mostRecent.getId());
        assertThat(((LatePaymentTerms) current.values()).gracePeriodDays()).isEqualTo(7);
    }

    @Test
    void aSurchargePercentOutsideZeroToOneHundredIsRejected() {
        Person director = persistPerson("director.policy3." + System.nanoTime());
        authenticateAs(director);

        assertThatThrownBy(() -> new CreateLatePaymentSettingsRequest(
                10, Percentage.ofPoints(BigDecimal.valueOf(150)), 30, 2, LocalDate.now()))
                .isInstanceOf(InvalidPercentageException.class);
        assertThatThrownBy(() -> new CreateLatePaymentSettingsRequest(
                10, Percentage.ofPoints(BigDecimal.valueOf(-10)), 30, 2, LocalDate.now()))
                .isInstanceOf(InvalidPercentageException.class);
    }
}
