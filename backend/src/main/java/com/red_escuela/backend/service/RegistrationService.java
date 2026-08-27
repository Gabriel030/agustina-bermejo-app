package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.RegisterRequest;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InstitutionNotFoundException;
import com.red_escuela.backend.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.StudentRepository;
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

    public RegistrationService(
            PersonRepository personRepository,
            StudentRepository studentRepository,
            InstitutionRepository institutionRepository,
            PersonFactory personFactory,
            PasswordEncoder passwordEncoder,
            AuthService authService) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public AuthTokens registrar(RegisterRequest request) {
        authService.validarPoliticaDePassword(request.password());

        if (personRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        Institution institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(request.institutionId()));

        Person person = personFactory.crearPersonConCredenciales(
                request.nombre(), request.apellido(), request.email(),
                request.username(), passwordEncoder.encode(request.password()));

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        // CONFIRMADO, no PROSPECTO: a diferencia de Prospect, acá no hay revisión
        // de documentación pendiente para la cuenta en sí — eso ocurre recién si
        // pide una carrera (ProgramEnrollmentApplication), no antes.
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);

        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        return authService.issueTokens(person);
    }
}
