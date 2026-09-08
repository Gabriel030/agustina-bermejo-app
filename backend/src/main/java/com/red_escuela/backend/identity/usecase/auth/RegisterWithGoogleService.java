package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InstitutionNotFoundException;
import com.red_escuela.backend.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.identity.service.PersonFactory;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// Épica 1 Fase 2 (Login con Google): mismo flujo que RegisterService, para cuando alguien
// confirma explícitamente que quiere crear una cuenta nueva con un email de Google que no
// coincide con ningún Person existente (LoginWithGoogleService tira GoogleAccountNotLinkedException
// antes de llegar acá, el frontend le muestra la confirmación y recién ahí se llama a esto).
@UseCase
public class RegisterWithGoogleService implements RegisterWithGoogleUseCase {

    private final GoogleIdTokenService googleIdTokenService;
    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonFactory personFactory;
    private final AuthTokenIssuer authTokenIssuer;

    public RegisterWithGoogleService(GoogleIdTokenService googleIdTokenService, PersonRepository personRepository,
                                      StudentRepository studentRepository, InstitutionRepository institutionRepository,
                                      PersonFactory personFactory, AuthTokenIssuer authTokenIssuer) {
        this.googleIdTokenService = googleIdTokenService;
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Override
    @Transactional
    public AuthTokens register(RegisterWithGoogleCommand command) {
        var profile = googleIdTokenService.verify(command.idToken());

        if (personRepository.findByEmail(profile.email()).isPresent()) {
            throw new UsernameAlreadyExistsException(profile.email());
        }

        Institution institution = institutionRepository.findById(command.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(command.institutionId()));

        try {
            Person person = personFactory.createPersonWithGoogle(
                    profile.givenName(), profile.familyName(), profile.email(), profile.subject());

            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            studentRepository.saveAndFlush(student);

            return authTokenIssuer.issueTokens(person);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(profile.email());
        }
    }
}
