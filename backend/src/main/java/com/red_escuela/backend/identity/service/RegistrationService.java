package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InstitutionNotFoundException;
import com.red_escuela.backend.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro independiente (Épica 7): a diferencia del alta de Prospect
 * (ProspectService, staff-driven, con revisión de documentación antes de dar
 * credenciales), acá la persona crea su propia cuenta y queda logueada de
 * inmediato — sin rol todavía, hasta su primera aprobación (ver
 * ProgramEnrollmentApplicationService / CourseEnrollmentService). Responsabilidad
 * separada de AuthService (login/refresh/logout/reset), que no crece con lógica
 * de alta de cuenta nueva.
 */
@Service
public class RegistrationService {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final GoogleIdTokenService googleIdTokenService;

    public RegistrationService(
            PersonRepository personRepository,
            StudentRepository studentRepository,
            InstitutionRepository institutionRepository,
            PersonFactory personFactory,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            GoogleIdTokenService googleIdTokenService) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.googleIdTokenService = googleIdTokenService;
    }

    @Transactional
    public AuthTokens register(RegisterRequest request) {
        authService.validarPoliticaDePassword(request.password());

        if (personRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        Institution institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(request.institutionId()));

        // El try/catch envuelve también la creación de Person (no solo el save de
        // Student): Person.id es GenerationType.IDENTITY, así que
        // personRepository.save(...) dentro de PersonFactory.createBasePerson ejecuta
        // el INSERT de inmediato (Hibernate no puede diferir/batchear un id IDENTITY),
        // no en el flush posterior de studentRepository.saveAndFlush(). Un email
        // duplicado con username distinto (uk_person_email, sin chequeo explícito acá
        // porque solo se valida unicidad de username) violaba el constraint ahí mismo,
        // afuera del try/catch original — DataIntegrityViolationException sin manejar,
        // encontrado probando la colección de Bruno (ver qa/bruno).
        try {
            Person person = personFactory.createPersonWithCredentials(
                    request.nombre(), request.apellido(), request.email(),
                    request.username(), passwordEncoder.encode(request.password()));

            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            // CONFIRMADO, no PROSPECTO: a diferencia de Prospect, acá no hay revisión
            // de documentación pendiente para la cuenta en sí — eso ocurre recién si
            // pide una carrera (ProgramEnrollmentApplication), no antes.
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            studentRepository.saveAndFlush(student);

            return authService.issueTokens(person);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(request.username());
        }
    }

    // Épica 1 Fase 2 (Login con Google): mismo flujo que register(), para cuando alguien
    // confirma explícitamente que quiere crear una cuenta nueva con un email de Google que no
    // coincide con ningún Person existente (AuthService.loginWithGoogle tira
    // GoogleAccountNotLinkedException antes de llegar acá, el frontend le muestra la
    // confirmación al usuario y recién ahí llama a este método con el mismo idToken).
    @Transactional
    public AuthTokens registerWithGoogle(String idToken, Long institutionId) {
        var profile = googleIdTokenService.verify(idToken);

        if (personRepository.findByEmail(profile.email()).isPresent()) {
            throw new UsernameAlreadyExistsException(profile.email());
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

            return authService.issueTokens(person);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(profile.email());
        }
    }
}
