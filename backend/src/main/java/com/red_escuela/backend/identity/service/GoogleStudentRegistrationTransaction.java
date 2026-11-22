package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleStudentRegistrationTransaction {
    private final GoogleIdTokenService googleIdTokenService;
    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonFactory personFactory;
    private final AuthTokenIssuer authTokenIssuer;

    public GoogleStudentRegistrationTransaction(GoogleIdTokenService googleIdTokenService,
                                                PersonRepository personRepository,
                                                StudentRepository studentRepository,
                                                InstitutionRepository institutionRepository,
                                                PersonFactory personFactory, AuthTokenIssuer authTokenIssuer) {
        this.googleIdTokenService = googleIdTokenService;
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Transactional
    public AuthTokens register(String idToken, Long institutionId) {
        var profile = googleIdTokenService.verify(idToken);

        if (personRepository.findByEmail(EmailAddress.of(profile.email())).isPresent()) {
            throw new UsernameAlreadyExistsException();
        }

        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

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
            throw new UsernameAlreadyExistsException();
        }
    }
}
