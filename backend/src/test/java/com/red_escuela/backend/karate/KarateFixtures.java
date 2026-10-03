package com.red_escuela.backend.karate;

import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.EvaluationSchemeTestRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

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

    public static Long createInstitution() {
        InstitutionRepository institutionRepository = context().getBean(InstitutionRepository.class);
        Institution institution = new Institution();
        institution.setName("Karate - Institución de prueba " + System.nanoTime());
        return institutionRepository.save(institution).getId();
    }

    public static String tokenForRole(String rol) {
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

    public static Map<String, Object> createConfirmedStudent(Long institutionId) {
        return createConfirmedStudentWithIdentity(institutionId, null, null, null);
    }

    public static Map<String, Object> createConfirmedStudentWithIdentity(Long institutionId, String name, String lastname, String dni) {
        AddressRepository addressRepository = context().getBean(AddressRepository.class);
        PersonRepository personRepository = context().getBean(PersonRepository.class);
        RoleRepository roleRepository = context().getBean(RoleRepository.class);
        StudentRepository studentRepository = context().getBean(StudentRepository.class);
        InstitutionRepository institutionRepository = context().getBean(InstitutionRepository.class);
        JwtService jwtService = context().getBean(JwtService.class);

        Address address = addressRepository.save(new Address());

        Person person = new Person();
        String username = "karate.alumno." + System.nanoTime();
        person.setName(name);
        person.setLastname(lastname);
        person.setDni(dni);
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

    public static Long createInstructor() {
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

    public static Map<String, Object> createTeacherWithToken() {
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

    public static Long createEvaluationScheme() {
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

    public static void completeStudyPlan(Long programEnrollmentId) {
        context().getBean(org.springframework.transaction.support.TransactionTemplate.class).executeWithoutResult(status -> {
            var enrollment = context().getBean(ProgramEnrollmentRepository.class).findById(programEnrollmentId)
                    .orElseThrow(() -> new IllegalStateException("ProgramEnrollment no encontrado: " + programEnrollmentId));
            var term = new com.red_escuela.backend.academicstructure.entity.Term();
            term.setYear(2026);
            term.setDescription("Karate plan completo");
            term.setStatus(StatusType.ACTIVE);
            context().getBean(com.red_escuela.backend.academicstructure.repository.TermRepository.class).save(term);
            var programSubjects = context().getBean(com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository.class)
                    .findByProgramId(enrollment.getProgram().getId());
            for (var programSubject : programSubjects) {
                var section = new com.red_escuela.backend.academicstructure.entity.SubjectSection();
                section.setName("Karate " + System.nanoTime());
                section.setSubject(programSubject.getSubject());
                section.setTerm(term);
                section.setStatus(StatusType.ACTIVE);
                context().getBean(com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository.class).save(section);
                var approved = new com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment();
                approved.setStudent(enrollment.getStudent());
                approved.setProgramEnrollment(enrollment);
                approved.setSubjectSection(section);
                approved.setStatus(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.APPROVED);
                approved.setFinalScore(8.0);
                approved.setEnrolledAt(java.time.LocalDateTime.of(2026, 3, 1, 10, 0));
                approved.setAttemptNumber(1);
                context().getBean(com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.class).save(approved);
            }
        });
    }

    public static Long createPendingInvoice(Long programEnrollmentId, String dueDate) {
        com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository programEnrollmentRepository =
                context().getBean(com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository.class);
        com.red_escuela.backend.billing.repository.InvoiceRepository invoiceRepository =
                context().getBean(com.red_escuela.backend.billing.repository.InvoiceRepository.class);

        var enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new IllegalStateException("ProgramEnrollment no encontrado: " + programEnrollmentId));

        com.red_escuela.backend.billing.entity.Invoice invoice = new com.red_escuela.backend.billing.entity.Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(com.red_escuela.backend.billing.entity.FeeType.RECURRING_FEE);
        java.time.LocalDate due = java.time.LocalDate.parse(dueDate);
        invoice.setPeriod(java.time.YearMonth.from(due));
        invoice.setDueDate(due);
        invoice.setBaseAmount(ars(5000));
        invoice.setStatus(com.red_escuela.backend.billing.entity.InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice).getId();
    }
}
