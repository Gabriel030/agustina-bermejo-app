package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.ProspectAlreadyConfirmedException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentOnboardingTest {
    private StudentRepository studentRepository;
    private InstitutionRepository institutionRepository;
    private BasePersonCreator basePersonCreator;
    private StudentRoleGranter studentRoleGranter;
    private StudentOnboarding studentOnboarding;

    @BeforeEach
    void createOnboarding() {
        studentRepository = mock(StudentRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        basePersonCreator = mock(BasePersonCreator.class);
        studentRoleGranter = mock(StudentRoleGranter.class);
        studentOnboarding = new StudentOnboarding(studentRepository, institutionRepository, basePersonCreator, studentRoleGranter);
    }

    private Person personWithAddress() {
        Person person = new Person();
        person.setName("Ana");
        person.setLastname("Paz");
        person.setEmail("ana@example.com");
        person.setAddress(new Address());
        return person;
    }

    private Student studentWith(Person person, StudentOnboardingStatus status) {
        Student student = new Student();
        student.setPerson(person);
        student.setOnboardingStatus(status);
        return student;
    }

    @Test
    void registerProspectSavesAProspectStudentOfTheInstitution() {
        Person person = personWithAddress();
        when(institutionRepository.findById(7L)).thenReturn(Optional.of(new Institution()));
        when(basePersonCreator.createBasePerson("Ana", "Paz", "ana@example.com")).thenReturn(person);
        when(studentRepository.save(any(Student.class))).thenAnswer(call -> call.getArgument(0));

        StudentOnboardingSummary summary = studentOnboarding.registerProspect(
                new NewProspect("Ana", "Paz", "ana@example.com", "1122", 7L));

        assertThat(summary.onboardingStatus()).isEqualTo(StudentOnboardingStatus.PROSPECTO);
        assertThat(summary.email()).isEqualTo("ana@example.com");
        assertThat(person.getPhoneNumber()).isEqualTo("1122");
    }

    @Test
    void registerProspectRejectsAnUnknownInstitution() {
        when(institutionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentOnboarding.registerProspect(new NewProspect("Ana", "Paz", "a@b.com", null, 7L)))
                .isInstanceOf(InstitutionNotFoundException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void saveProspectDraftOnlyOverwritesTheProvidedAddressFields() {
        Person person = personWithAddress();
        person.getAddress().setCity("Rosario");
        person.getAddress().setStreet("Vieja");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(studentWith(person, StudentOnboardingStatus.PROSPECTO)));

        studentOnboarding.saveProspectDraft(1L, new ProspectDraft(null, null, "Nueva", null, null, null, null, null, null));

        assertThat(person.getAddress().getStreet()).isEqualTo("Nueva");
        assertThat(person.getAddress().getCity()).isEqualTo("Rosario");
    }

    @Test
    void saveProspectDraftReportsADuplicatedDocument() {
        Student student = studentWith(personWithAddress(), StudentOnboardingStatus.PROSPECTO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(student)).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> studentOnboarding.saveProspectDraft(1L,
                new ProspectDraft("12345678", DocumentType.DNI, null, null, null, null, null, null, null)))
                .isInstanceOf(DniAlreadyExistsException.class);
    }

    @Test
    void saveProspectDraftRejectsAnUnknownStudent() {
        when(studentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentOnboarding.saveProspectDraft(1L,
                new ProspectDraft(null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    void confirmProspectGrantsTheStudentRoleAndMarksTheStudentConfirmed() {
        Person person = personWithAddress();
        Student student = studentWith(person, StudentOnboardingStatus.PROSPECTO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        StudentOnboardingSummary summary = studentOnboarding.confirmProspect(1L,
                new ConfirmedProspect("12345678", DocumentType.DNI, "Calle", "1", null, "Rosario", null, null, null));

        assertThat(summary.onboardingStatus()).isEqualTo(StudentOnboardingStatus.CONFIRMADO);
        assertThat(person.getUsername()).isEqualTo("12345678");
        assertThat(person.getAddress().getCity()).isEqualTo("Rosario");
        verify(studentRoleGranter).grantStudentRoleIfMissing(person);
        verify(studentRepository).saveAndFlush(student);
    }

    @Test
    void confirmProspectRejectsAStudentThatWasAlreadyConfirmed() {
        Student student = studentWith(personWithAddress(), StudentOnboardingStatus.CONFIRMADO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                new ConfirmedProspect("1", DocumentType.DNI, "c", null, null, "r", null, null, null)))
                .isInstanceOf(ProspectAlreadyConfirmedException.class);
        verify(studentRoleGranter, never()).grantStudentRoleIfMissing(any());
    }

    @Test
    void confirmProspectReportsADuplicatedDocument() {
        Student student = studentWith(personWithAddress(), StudentOnboardingStatus.PROSPECTO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(student)).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> studentOnboarding.confirmProspect(1L,
                new ConfirmedProspect("12345678", DocumentType.DNI, "c", null, null, "r", null, null, null)))
                .isInstanceOf(DniAlreadyExistsException.class);
    }
}
