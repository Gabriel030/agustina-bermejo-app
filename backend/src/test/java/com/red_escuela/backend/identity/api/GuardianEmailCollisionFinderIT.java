package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.LegalGuardianConfirmation;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuardianEmailCollisionFinderIT extends AbstractIntegrationTest {

    private static final String SHARED_EMAIL = "tutor-collision@example.com";

    @Autowired
    private GuardianEmailCollisionFinder guardianFinder;

    @Autowired
    private MinorGuardianEmailCollisionFinder minorFinder;

    @Autowired
    private EntityManager entityManager;

    private int sequence;

    private Person person(LocalDate birthDate, String email) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = "guardian-collision-" + (sequence++);
        person.setUsername(username);
        person.setName("Nombre");
        person.setLastname(username);
        person.setEmail(email);
        person.setBirthDate(birthDate);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private static LocalDate minorBirthDate() {
        return ApplicationTime.today().minusYears(15);
    }

    private void studentGuardianEmail(Person owner, String guardianEmail) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(owner);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student.setGuardianEmail(guardianEmail);
        entityManager.persist(student);
        entityManager.flush();
    }

    private void legalConfirmationGuardianEmail(Person owner, String guardianEmail, LocalDateTime expiresAt, boolean used) {
        LocalDateTime now = LocalDateTime.now();
        LegalGuardianConfirmation confirmation = new LegalGuardianConfirmation(owner.getId(), "Tutor", guardianEmail, "Madre",
                "h" + owner.getId(), now.minusDays(30), expiresAt);
        entityManager.persist(confirmation);
        entityManager.flush();
        if (used) {
            entityManager.createNativeQuery("update legal_guardian_confirmation set used_at = :usedAt where id = :id")
                    .setParameter("usedAt", now.minusDays(2))
                    .setParameter("id", confirmation.getId())
                    .executeUpdate();
        }
    }

    @Test
    void minorEmailEqualToStudentGuardianEmailCollides() {
        Person minor = person(minorBirthDate(), " " + SHARED_EMAIL.toUpperCase() + " ");
        studentGuardianEmail(person(minorBirthDate(), null), SHARED_EMAIL);

        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).containsExactly(minor.getId());
        assertThat(guardianFinder.findPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).containsExactly(minor.getId());
    }

    @Test
    void minorEmailEqualToLegalGuardianConfirmationEmailCollidesIgnoringCaseAndSpaces() {
        Person minor = person(minorBirthDate(), SHARED_EMAIL.toUpperCase());
        legalConfirmationGuardianEmail(minor, " " + SHARED_EMAIL + " ", LocalDateTime.now().plusDays(1), false);

        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).containsExactly(minor.getId());
        assertThat(guardianFinder.findPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).containsExactly(minor.getId());
    }

    @Test
    void expiredAndUsedLegalGuardianConfirmationsStillCollide() {
        Person expired = person(minorBirthDate(), "expired-" + SHARED_EMAIL);
        legalConfirmationGuardianEmail(expired, "expired-" + SHARED_EMAIL, LocalDateTime.now().minusDays(1), false);
        Person used = person(minorBirthDate(), "used-" + SHARED_EMAIL);
        legalConfirmationGuardianEmail(used, "used-" + SHARED_EMAIL, LocalDateTime.now().minusDays(1), true);

        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of(expired.getId(), used.getId())))
                .containsExactlyInAnyOrder(expired.getId(), used.getId());
    }

    @Test
    void minorWithDistinctEmailDoesNotCollide() {
        Person minor = person(minorBirthDate(), "own-" + SHARED_EMAIL);
        legalConfirmationGuardianEmail(minor, SHARED_EMAIL, LocalDateTime.now().plusDays(1), false);
        studentGuardianEmail(person(minorBirthDate(), null), "other-" + SHARED_EMAIL);

        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).isEmpty();
        assertThat(guardianFinder.findPersonIdsWithEmailSharedWithGuardian(List.of(minor.getId()))).isEmpty();
    }

    @Test
    void adultSharingEmailWithLegalGuardianConfirmationIsIgnoredByMinorFinder() {
        Person adult = person(ApplicationTime.today().minusYears(30), SHARED_EMAIL);
        legalConfirmationGuardianEmail(person(minorBirthDate(), null), SHARED_EMAIL, LocalDateTime.now().plusDays(1), false);

        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of(adult.getId()))).isEmpty();
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(minorFinder.findMinorPersonIdsWithEmailSharedWithGuardian(List.of())).isEmpty();
        assertThat(guardianFinder.findPersonIdsWithEmailSharedWithGuardian(List.of())).isEmpty();
    }
}
