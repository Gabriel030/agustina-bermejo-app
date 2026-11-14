package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import com.red_escuela.backend.identity.specialist.LegalRegistrationCompleter;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RegisterService implements RegisterUseCase {
    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthTokenIssuer authTokenIssuer;
    private final LegalRegistrationCompleter legalRegistrationCompleter;

    public RegisterService(PersonRepository personRepository, StudentRepository studentRepository,
                            InstitutionRepository institutionRepository, PersonFactory personFactory,
                            PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator,
                            AuthTokenIssuer authTokenIssuer, LegalRegistrationCompleter legalRegistrationCompleter) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.authTokenIssuer = authTokenIssuer;
        this.legalRegistrationCompleter = legalRegistrationCompleter;
    }

    @Override
    @Transactional
    public AuthTokens register(RegisterRequest request) {
        legalRegistrationCompleter.requireValid(request);
        passwordPolicyValidator.validate(request.password());

        if (personRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        Institution institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(request.institutionId()));

        try {
            Person person = personFactory.createPersonWithCredentials(
                    request.nombre(), request.apellido(), request.email(),
                    request.username(), passwordEncoder.encode(request.password()));

            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            studentRepository.saveAndFlush(student);

            legalRegistrationCompleter.complete(person, request);

            return authTokenIssuer.issueTokens(person);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(request.username());
        }
    }
}
