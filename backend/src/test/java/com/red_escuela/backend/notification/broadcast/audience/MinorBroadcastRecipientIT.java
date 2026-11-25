package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MinorBroadcastRecipientIT extends AbstractIntegrationTest {

    private static final String GUARDIAN_EMAIL = "tutor-minors@example.com";
    private static final String VALID_PHONE = "+5491155551234";

    @Autowired
    private RecipientEligibilityEvaluator evaluator;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private RoleRepository roleRepository;

    private Institution institution;
    private int sequence;

    private Person person(LocalDate birthDate, String email) {
        Person person = AudienceTestPersons.create(entityManager, roleRepository, "minor-bc-" + (sequence++), true, null);
        person.setBirthDate(birthDate);
        person.setEmail(email);
        entityManager.flush();
        return person;
    }

    private Person personWithOwnEmail(LocalDate birthDate) {
        Person person = AudienceTestPersons.create(entityManager, roleRepository, "minor-bc-" + (sequence++), true, null);
        person.setBirthDate(birthDate);
        entityManager.flush();
        return person;
    }

    private void studentWithGuardianEmail(Person person, String guardianEmail) {
        if (institution == null) {
            institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
        }
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student.setGuardianEmail(guardianEmail);
        entityManager.persist(student);
        entityManager.flush();
    }

    private static AudienceRecipient recipientOf(Person person) {
        String email = person.getEmail() == null ? null : person.getEmail().value();
        return new AudienceRecipient(person.getId(), person.getName(), email, Boolean.TRUE.equals(person.getEmailVerified()), person.getPhoneNumber());
    }

    private Optional<ExclusionReason> batchReason(Person person) {
        return Optional.ofNullable(evaluator.findExclusionReasons(List.of(recipientOf(person)), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL).get(person.getId()));
    }

    private Optional<ExclusionReason> singleReason(Person person) {
        return evaluator.findExclusionReason(recipientOf(person), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL);
    }

    private static LocalDate minorBirthDate() {
        return ApplicationTime.today().minusYears(15);
    }

    @Test
    void minorWithoutOwnEmailIsExcludedWithNoChannelAddress() {
        Person minor = person(minorBirthDate(), null);
        studentWithGuardianEmail(minor, GUARDIAN_EMAIL);

        assertThat(batchReason(minor)).contains(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(singleReason(minor)).contains(ExclusionReason.NO_CHANNEL_ADDRESS);
    }

    @Test
    void minorWithEmailEqualToGuardianEmailIsExcludedIgnoringCaseAndSpaces() {
        Person minor = person(minorBirthDate(), GUARDIAN_EMAIL.toUpperCase());
        studentWithGuardianEmail(minor, " " + GUARDIAN_EMAIL + " ");

        assertThat(batchReason(minor)).contains(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(singleReason(minor)).contains(ExclusionReason.NO_CHANNEL_ADDRESS);
    }

    @Test
    void minorWithOwnEmailDifferentFromGuardianEmailIsEligible() {
        Person minor = personWithOwnEmail(minorBirthDate());
        studentWithGuardianEmail(minor, GUARDIAN_EMAIL);

        assertThat(batchReason(minor)).isEmpty();
        assertThat(singleReason(minor)).isEmpty();
    }

    @Test
    void adultSharingEmailWithGuardianKeepsBeingEligible() {
        Person adult = person(ApplicationTime.today().minusYears(30), GUARDIAN_EMAIL);
        studentWithGuardianEmail(personWithOwnEmail(minorBirthDate()), GUARDIAN_EMAIL);

        assertThat(batchReason(adult)).isEmpty();
        assertThat(singleReason(adult)).isEmpty();
    }

    @Test
    void personTurningEighteenTodayIsAdultAndOneDayYoungerIsMinor() {
        LocalDate today = ApplicationTime.today();
        Person adultToday = person(today.minusYears(18), "adult-today@example.com");
        Person minorTomorrow = person(today.minusYears(18).plusDays(1), "minor-tomorrow@example.com");
        studentWithGuardianEmail(personWithOwnEmail(minorBirthDate()), "adult-today@example.com");
        studentWithGuardianEmail(personWithOwnEmail(minorBirthDate()), "minor-tomorrow@example.com");

        assertThat(batchReason(adultToday)).isEmpty();
        assertThat(batchReason(minorTomorrow)).contains(ExclusionReason.NO_CHANNEL_ADDRESS);
    }

    @Test
    void personWithoutBirthDateIsNotTreatedAsMinor() {
        Person unknownAge = person(null, GUARDIAN_EMAIL);
        studentWithGuardianEmail(personWithOwnEmail(minorBirthDate()), GUARDIAN_EMAIL);

        assertThat(batchReason(unknownAge)).isEmpty();
    }

    @Test
    void whatsappChannelIgnoresGuardianEmailCollision() {
        Person minor = person(minorBirthDate(), GUARDIAN_EMAIL);
        minor.setPhoneNumber(VALID_PHONE);
        entityManager.flush();
        studentWithGuardianEmail(minor, GUARDIAN_EMAIL);

        assertThat(evaluator.findExclusionReasons(List.of(recipientOf(minor)), ChannelType.WHATSAPP_LINK, BroadcastCategory.INSTITUTIONAL))
                .doesNotContainKey(minor.getId());
    }

    private List<AudienceRecipient> mixedAudience(int size) {
        List<AudienceRecipient> recipients = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            LocalDate birthDate = i % 2 == 0 ? minorBirthDate() : ApplicationTime.today().minusYears(30);
            if (i % 3 == 0) {
                String email = "bulk-" + i + "@example.com";
                recipients.add(recipientOf(person(birthDate, email)));
                studentWithGuardianEmail(personWithOwnEmail(minorBirthDate()), email);
            } else {
                recipients.add(recipientOf(personWithOwnEmail(birthDate)));
            }
        }
        entityManager.flush();
        entityManager.clear();
        return recipients;
    }

    private Map<Long, ExclusionReason> evaluateCounting(List<AudienceRecipient> recipients, int maxQueries) {
        return QueryCountAssertions.assertAtMostQueries(entityManagerFactory, maxQueries,
                () -> evaluator.findExclusionReasons(recipients, ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL));
    }

    @Test
    void minorLookupUsesOneQueryForASingleChunk() {
        Map<Long, ExclusionReason> reasons = evaluateCounting(mixedAudience(60), 3);

        assertThat(reasons.values()).containsOnly(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(reasons).hasSize(10);
    }

    @Test
    void minorLookupAddsExactlyOneQueryPerChunkOfFiveHundred() {
        Map<Long, ExclusionReason> reasons = evaluateCounting(mixedAudience(1001), 7);

        assertThat(reasons.values()).containsOnly(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(reasons).hasSize(167);
    }
}
