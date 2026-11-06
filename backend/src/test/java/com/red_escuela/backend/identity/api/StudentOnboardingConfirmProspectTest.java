package com.red_escuela.backend.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.BirthDateRequiredException;
import com.red_escuela.backend.identity.exception.GuardianRequiredForMinorException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.InvalidDocumentNumberException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentOnboardingConfirmProspectTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    private static final GuardianContact GUARDIAN =
            new GuardianContact("Luis Paz", "20111222", "Padre", "+5493410000", "luis@x.com");
    private static final EmergencyContact EMERGENCY = new EmergencyContact("Marta Paz", "Madre", "+5493411111");

    private StudentRepository studentRepository;
    private StudentOnboarding studentOnboarding;
    private Student student;
    private Person person;

    @BeforeEach
    void createOnboarding() {
        ApplicationTime.useClock(Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneId.of("UTC")));
        studentRepository = mock(StudentRepository.class);
        studentOnboarding = new StudentOnboarding(studentRepository, mock(InstitutionRepository.class),
                mock(BasePersonCreator.class), mock(StudentRoleGranter.class));
        person = new Person();
        person.setName("Ana");
        person.setLastname("Paz");
        person.setEmail("ana@example.com");
        person.setAddress(new Address());
        student = new Student();
        student.setPerson(person);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
    }

    @AfterEach
    void restoreClock() {
        ApplicationTime.useClock(Clock.systemDefaultZone());
    }

    private ConfirmedProspect confirmation(LocalDate birthDate, GuardianContact guardian, EmergencyContact emergency) {
        return new ConfirmedProspect("12345678", DocumentType.DNI, "Mitre", "10", null, "Rosario", null, null, null,
                birthDate, "F", "Argentina", guardian, emergency);
    }

    @Test
    void confirmAdultWithoutGuardianSucceeds() {
        studentOnboarding.confirmProspect(1L, confirmation(LocalDate.of(1990, 5, 1), null, null));

        assertThat(student.getOnboardingStatus()).isEqualTo(StudentOnboardingStatus.CONFIRMADO);
        assertThat(person.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 1));
        assertThat(person.getSex()).isEqualTo("F");
        assertThat(person.getNationality()).isEqualTo("Argentina");
        assertThat(student.getGuardianFullName()).isNull();
    }

    @Test
    void confirmOnExactEighteenthBirthdayIsAdult() {
        studentOnboarding.confirmProspect(1L, confirmation(TODAY.minusYears(18), null, null));

        assertThat(student.getOnboardingStatus()).isEqualTo(StudentOnboardingStatus.CONFIRMADO);
    }

    @Test
    void confirmTheDayBeforeEighteenthBirthdayWithoutGuardianThrows() {
        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(TODAY.minusYears(18).plusDays(1), null, null)))
                .isInstanceOf(GuardianRequiredForMinorException.class);
        assertThat(student.getOnboardingStatus()).isEqualTo(StudentOnboardingStatus.PROSPECTO);
    }

    @Test
    void confirmMinorWithoutEmergencyContactThrows() {
        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(LocalDate.of(2012, 3, 1), GUARDIAN, null)))
                .isInstanceOf(GuardianRequiredForMinorException.class);
    }

    @Test
    void confirmMinorWithoutGuardianThrows() {
        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(LocalDate.of(2012, 3, 1), null, EMERGENCY)))
                .isInstanceOf(GuardianRequiredForMinorException.class);
    }

    @Test
    void confirmMinorWithGuardianMissingDocumentNumberThrows() {
        GuardianContact withoutDocument = new GuardianContact("Luis Paz", " ", "Padre", "+5493410000", null);

        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(LocalDate.of(2012, 3, 1), withoutDocument, EMERGENCY)))
                .isInstanceOf(GuardianRequiredForMinorException.class);
    }

    @Test
    void confirmMinorWithEmergencyContactMissingRelationshipThrows() {
        EmergencyContact withoutRelationship = new EmergencyContact("Marta Paz", null, "+5493411111");

        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(LocalDate.of(2012, 3, 1), GUARDIAN, withoutRelationship)))
                .isInstanceOf(GuardianRequiredForMinorException.class);
    }

    @Test
    void confirmMinorWithFullDataStoresGuardianAndEmergencyContact() {
        studentOnboarding.confirmProspect(1L, confirmation(LocalDate.of(2012, 3, 1), GUARDIAN, EMERGENCY));

        assertThat(student.getGuardianFullName()).isEqualTo("Luis Paz");
        assertThat(student.getGuardianDni()).isEqualTo("20111222");
        assertThat(student.getGuardianRelationship()).isEqualTo("Padre");
        assertThat(student.getGuardianPhone()).isEqualTo("+5493410000");
        assertThat(student.getGuardianEmail()).isEqualTo("luis@x.com");
        assertThat(student.getEmergencyContactName()).isEqualTo("Marta Paz");
        assertThat(student.getEmergencyContactRelationship()).isEqualTo("Madre");
        assertThat(student.getEmergencyContactPhone()).isEqualTo("+5493411111");
    }

    @Test
    void confirmMinorWithMalformedGuardianDocumentRejectsTheDocument() {
        GuardianContact malformed = new GuardianContact("Luis Paz", "12.345", "Padre", "+5493410000", null);

        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                confirmation(LocalDate.of(2012, 3, 1), malformed, EMERGENCY)))
                .isInstanceOf(InvalidDocumentNumberException.class);
    }

    @Test
    void confirmAdultStoresGuardianWhenProvided() {
        studentOnboarding.confirmProspect(1L, confirmation(LocalDate.of(1990, 5, 1), GUARDIAN, EMERGENCY));

        assertThat(student.getGuardianFullName()).isEqualTo("Luis Paz");
        assertThat(student.getEmergencyContactName()).isEqualTo("Marta Paz");
    }

    @Test
    void confirmWithoutBirthDateThrowsBirthDateRequired() {
        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L, confirmation(null, GUARDIAN, EMERGENCY)))
                .isInstanceOf(BirthDateRequiredException.class);
    }

    @Test
    void saveProspectDraftKeepsExistingGuardianFieldsThatAreNotSent() {
        student.setGuardianFullName("Luis Paz");
        student.setGuardianPhone("111");

        studentOnboarding.saveProspectDraft(1L, new ProspectDraft(null, null, null, null, null, null, null, null, null,
                LocalDate.of(2012, 3, 1), null, null, new GuardianContact(null, null, "Padre", "222", null), null));

        assertThat(student.getGuardianFullName()).isEqualTo("Luis Paz");
        assertThat(student.getGuardianRelationship()).isEqualTo("Padre");
        assertThat(student.getGuardianPhone()).isEqualTo("222");
        assertThat(person.getBirthDate()).isEqualTo(LocalDate.of(2012, 3, 1));
    }
}
