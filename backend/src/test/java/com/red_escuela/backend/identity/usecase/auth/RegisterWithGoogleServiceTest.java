package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.service.PersonFactory;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegisterWithGoogleServiceTest {

    private static final String ID_TOKEN = "google-id-token";
    private static final Long INSTITUTION_ID = 3L;
    private static final GoogleIdTokenService.GoogleProfile PROFILE =
            new GoogleIdTokenService.GoogleProfile("google-sub", "Alumno@Example.com", "Lucia", "Fernandez");

    private final GoogleIdTokenService googleIdTokenService = mock(GoogleIdTokenService.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final PersonFactory personFactory = mock(PersonFactory.class);
    private final AuthTokenIssuer authTokenIssuer = mock(AuthTokenIssuer.class);
    private final RegisterWithGoogleService service = new RegisterWithGoogleService(
            googleIdTokenService, personRepository, studentRepository, institutionRepository, personFactory,
            authTokenIssuer);
    private final RegisterWithGoogleCommand command = new RegisterWithGoogleCommand(ID_TOKEN, INSTITUTION_ID);

    private void verifiedTokenWithFreeEmailAndExistingInstitution() {
        when(googleIdTokenService.verify(ID_TOKEN)).thenReturn(PROFILE);
        when(personRepository.findByEmail(EmailAddress.of(PROFILE.email()))).thenReturn(Optional.empty());
    }

    @Test
    void aVerifiedGoogleAccountRegistersAConfirmedStudentAndReceivesTokens() {
        verifiedTokenWithFreeEmailAndExistingInstitution();
        Institution institution = new Institution();
        Person person = new Person();
        AuthTokens tokens = new AuthTokens("access", "refresh", 900L);
        when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution));
        when(personFactory.createPersonWithGoogle("Lucia", "Fernandez", PROFILE.email(), "google-sub"))
                .thenReturn(person);
        when(authTokenIssuer.issueTokens(person)).thenReturn(tokens);

        AuthTokens result = service.register(command);

        assertThat(result).isSameAs(tokens);
        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPerson()).isSameAs(person);
        assertThat(saved.getValue().getInstitution()).isSameAs(institution);
        assertThat(saved.getValue().getOnboardingStatus()).isEqualTo(StudentOnboardingStatus.CONFIRMADO);
    }

    @Test
    void anAlreadyRegisteredEmailIsRejectedWithoutCreatingAnything() {
        when(googleIdTokenService.verify(ID_TOKEN)).thenReturn(PROFILE);
        when(personRepository.findByEmail(EmailAddress.of(PROFILE.email()))).thenReturn(Optional.of(new Person()));

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(UsernameAlreadyExistsException.class);
        verifyNoInteractions(institutionRepository, personFactory, studentRepository, authTokenIssuer);
    }

    @Test
    void anUnknownInstitutionIsRejectedWithoutCreatingAnything() {
        verifiedTokenWithFreeEmailAndExistingInstitution();
        when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(InstitutionNotFoundException.class);
        verifyNoInteractions(personFactory, studentRepository, authTokenIssuer);
    }

    @Test
    void aRaceOnTheUniqueEmailIsReportedAsAlreadyRegisteredAndIssuesNoTokens() {
        verifiedTokenWithFreeEmailAndExistingInstitution();
        when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.of(new Institution()));
        when(personFactory.createPersonWithGoogle(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Person());
        when(studentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(UsernameAlreadyExistsException.class);
        verify(authTokenIssuer, never()).issueTokens(any());
    }

    @Test
    void anInvalidGoogleTokenIsRejectedBeforeTouchingAnyRepository() {
        when(googleIdTokenService.verify(ID_TOKEN)).thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(personRepository, institutionRepository, personFactory, studentRepository,
                authTokenIssuer);
    }
}
