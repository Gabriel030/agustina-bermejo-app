package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InstitutionNotFoundException;
import com.red_escuela.backend.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.service.PasswordPolicyValidator;
import com.red_escuela.backend.identity.service.PersonFactory;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

// Registro independiente (Épica 7): a diferencia del alta de Prospect (staff-driven, con
// revisión de documentación antes de dar credenciales), acá la persona crea su propia cuenta
// y queda logueada de inmediato — sin rol todavía, hasta su primera aprobación.
@UseCase
public class RegisterService implements RegisterUseCase {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthTokenIssuer authTokenIssuer;

    public RegisterService(PersonRepository personRepository, StudentRepository studentRepository,
                            InstitutionRepository institutionRepository, PersonFactory personFactory,
                            PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator,
                            AuthTokenIssuer authTokenIssuer) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Override
    @Transactional
    public AuthTokens register(RegisterRequest request) {
        passwordPolicyValidator.validate(request.password());

        if (personRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        Institution institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(request.institutionId()));

        // El try/catch envuelve también la creación de Person (no solo el save de Student):
        // Person.id es GenerationType.IDENTITY, así que personRepository.save(...) dentro de
        // PersonFactory.createBasePerson ejecuta el INSERT de inmediato — un email duplicado
        // con username distinto (uk_person_email) viola el constraint ahí mismo.
        try {
            Person person = personFactory.createPersonWithCredentials(
                    request.nombre(), request.apellido(), request.email(),
                    request.username(), passwordEncoder.encode(request.password()));

            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            // CONFIRMADO, no PROSPECTO: acá no hay revisión de documentación pendiente para
            // la cuenta en sí — eso ocurre recién si pide una carrera.
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            studentRepository.saveAndFlush(student);

            return authTokenIssuer.issueTokens(person);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(request.username());
        }
    }
}
