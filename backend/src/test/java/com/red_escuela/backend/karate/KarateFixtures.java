package com.red_escuela.backend.karate;

import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.GradeAggregationMethod;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.EvaluationSchemeTestRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.service.JwtService;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Puente Java para setup que no tiene sentido hacer vía HTTP desde un .feature de Karate
 * (create un token de staff sin pasar por un flujo de alta completo, sembrar una
 * institución) — mismo criterio que RegistrationFlowTest.persistInstitution()/
 * tokenDePreceptor(), reusado acá porque Karate llama a métodos Java estáticos
 * directamente (Java.type(...) desde el .feature o karate-config.js).
 *
 * Usa los repositories (cada método de un JpaRepository es transaccional por sí solo,
 * SimpleJpaRepository lo anota @Transactional) en vez de EntityManager.persist() crudo
 * a propósito: KarateSuiteTest NO es @Transactional (si lo fuera, toda la corrida
 * compartiría una única transacción de test y enmascararía bugs reales de boundary
 * transaccional — exactamente el problema que encontramos con RegistrationFlowTest
 * y el bug del rol ALUMNO no otorgado). Sin @Transactional en el test, un
 * EntityManager.persist() suelto no tiene transacción activa donde flushear.
 *
 * KarateSpringContext.set(...) lo llama el runner (@BeforeEach) para dejar disponible el
 * ApplicationContext de la corrida — Karate no inyecta beans de Spring por sí solo.
 */
public class KarateFixtures {

    private static ApplicationContext context() {
        ApplicationContext context = KarateSpringContext.get();
        if (context == null) {
            throw new IllegalStateException(
                    "KarateSpringContext no está seteado — el runner tiene que llamar a "
                            + "KarateSpringContext.set(applicationContext) en un @BeforeEach.");
        }
        return context;
    }

    public static Long crearInstitucion() {
        InstitutionRepository institutionRepository = context().getBean(InstitutionRepository.class);
        Institution institution = new Institution();
        institution.setName("Karate - Institución de prueba " + System.nanoTime());
        return institutionRepository.save(institution).getId();
    }

    // rol: "PRECEPTOR", "ADMINISTRATIVO", etc. — mismo criterio que
    // RegistrationFlowTest.tokenDePreceptor(), generalizado a cualquier rol de staff.
    public static String tokenParaRol(String rol) {
        AddressRepository addressRepository = context().getBean(AddressRepository.class);
        PersonRepository personRepository = context().getBean(PersonRepository.class);
        RoleRepository roleRepository = context().getBean(RoleRepository.class);
        JwtService jwtService = context().getBean(JwtService.class);

        Address address = addressRepository.save(new Address());

        Person person = new Person();
        String username = "karate." + rol.toLowerCase() + "." + System.nanoTime();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(rol).orElseThrow(
                () -> new IllegalStateException("Rol sembrado por Flyway no encontrado: " + rol));
        person.getRoles().add(role);
        person = personRepository.save(person);

        return jwtService.generateAccessToken(person);
    }

    // Alumno CONFIRMADO con studentId conocido, sin pasar por /auth/register (que no
    // devuelve el studentId en la respuesta, solo tokens) — necesario para casos que
    // arman datos vía staff (facturas, notas) contra un id de alumno concreto.
    public static Map<String, Object> crearAlumnoConfirmado(Long institutionId) {
        AddressRepository addressRepository = context().getBean(AddressRepository.class);
        PersonRepository personRepository = context().getBean(PersonRepository.class);
        RoleRepository roleRepository = context().getBean(RoleRepository.class);
        StudentRepository studentRepository = context().getBean(StudentRepository.class);
        InstitutionRepository institutionRepository = context().getBean(InstitutionRepository.class);
        JwtService jwtService = context().getBean(JwtService.class);

        Address address = addressRepository.save(new Address());

        Person person = new Person();
        String username = "karate.alumno." + System.nanoTime();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ALUMNO").orElseThrow(
                () -> new IllegalStateException("Rol sembrado por Flyway no encontrado: ALUMNO"));
        person.getRoles().add(role);
        person = personRepository.save(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institutionRepository.findById(institutionId).orElseThrow());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", student.getId());
        result.put("accessToken", jwtService.generateAccessToken(person));
        return result;
    }

    // Instructor sin credenciales de login — solo hace falta el id para asignarlo a una
    // comisión (CourseSectionController/SubjectSectionController). No pasa por /staff porque
    // StaffResponse.id es el id de Person, no de Instructor (ver comentario en StaffResponse) y
    // acá necesitamos el id de Instructor directo.
    public static Long crearInstructor() {
        AddressRepository addressRepository = context().getBean(AddressRepository.class);
        PersonRepository personRepository = context().getBean(PersonRepository.class);
        InstructorRepository instructorRepository = context().getBean(InstructorRepository.class);

        Address address = addressRepository.save(new Address());

        Person person = new Person();
        String username = "karate.instructor." + System.nanoTime();
        person.setName("Docente");
        person.setLastname("Karate");
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);

        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        return instructorRepository.save(instructor).getId();
    }

    // A diferencia de crearInstructor(), acá el Docente SÍ necesita loguearse: carga
    // asistencia/notas vía AttendanceController/GradeItemController, que resuelven el
    // Instructor autenticado por JWT (CurrentInstructorService), no por un id de la URL.
    public static Map<String, Object> crearDocenteConToken() {
        AddressRepository addressRepository = context().getBean(AddressRepository.class);
        PersonRepository personRepository = context().getBean(PersonRepository.class);
        RoleRepository roleRepository = context().getBean(RoleRepository.class);
        InstructorRepository instructorRepository = context().getBean(InstructorRepository.class);
        JwtService jwtService = context().getBean(JwtService.class);

        Address address = addressRepository.save(new Address());

        Person person = new Person();
        String username = "karate.docente." + System.nanoTime();
        person.setName("Docente");
        person.setLastname("Karate");
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("DOCENTE").orElseThrow(
                () -> new IllegalStateException("Rol sembrado por Flyway no encontrado: DOCENTE"));
        person.getRoles().add(role);
        person = personRepository.save(person);

        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        instructor = instructorRepository.save(instructor);

        Map<String, Object> result = new HashMap<>();
        result.put("instructorId", instructor.getId());
        result.put("accessToken", jwtService.generateAccessToken(person));
        return result;
    }

    // No hay ningún endpoint de alta para EvaluationScheme (se referencia por id, opcional,
    // desde CreateSectionRequest/UpdateSectionRequest — ver "Deuda técnica y gaps de QA" en
    // Obsidian) — sin uno, GradeItemService.cargarNota* rechaza con NoEvaluationSchemeException
    // cualquier carga de nota sobre una comisión sin esquema asignado.
    public static Long crearEvaluationScheme() {
        EvaluationSchemeTestRepository evaluationSchemeRepository =
                context().getBean(EvaluationSchemeTestRepository.class);

        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema Karate " + System.nanoTime());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(1);
        scheme.setHasFinal(true);
        scheme.setFinalWeight(0.5);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(4.0);
        scheme.setMinApprovalGrade(6.0);
        return evaluationSchemeRepository.save(scheme).getId();
    }
}
