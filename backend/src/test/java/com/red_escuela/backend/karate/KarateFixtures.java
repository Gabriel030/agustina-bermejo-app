package com.red_escuela.backend.karate;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;

import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.repository.EvaluationSchemeTestRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
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

    public static void activateSubject(Long subjectId) {
        SubjectRepository subjectRepository = context().getBean(SubjectRepository.class);
        Subject subject = subjectRepository.findById(subjectId).orElseThrow();
        subject.setStatus(SubjectStatus.ACTIVE);
        subjectRepository.save(subject);
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
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
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
                approved.setFinalScore(Grade.of(BigDecimal.valueOf(8.0)));
                approved.setEnrolledAt(java.time.LocalDateTime.of(2026, 3, 1, 10, 0));
                approved.setAttemptNumber(1);
                context().getBean(com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.class).save(approved);
            }
        });
    }

    public static Long createPendingInvoice(Long programEnrollmentId, String dueDate) {
        com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository programEnrollmentRepository =
                context().getBean(com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository.class);

        var enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new IllegalStateException("ProgramEnrollment no encontrado: " + programEnrollmentId));

        java.time.LocalDate due = java.time.LocalDate.parse(dueDate);
        return BillingTestFixtures.invoice()
                .forProgramEnrollment(enrollment)
                .ofType(com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT)
                .forPeriod(java.time.YearMonth.from(due))
                .dueOn(due)
                .withBaseAmount(ars(5000))
                .pending()
                .build()
                .getId();
    }

    public static Long createIssuedInvoiceWithLedgerDebit(Long studentId, String amount) {
        var transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                context().getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var accountService = context().getBean(com.red_escuela.backend.billing.account.service.StudentCurrentAccountService.class);
        var invoiceRepository = context().getBean(com.red_escuela.backend.billing.invoice.repository.InvoiceRepository.class);
        var numberGenerator = context().getBean(com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator.class);
        var studentRepository = context().getBean(StudentRepository.class);
        return transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(studentId).orElseThrow().getPerson();
            var account = accountService.findOrOpenAndLockStudentCurrentAccount(studentId, com.red_escuela.backend.shared.domain.Currency.ARS);
            var snapshot = new com.red_escuela.backend.billing.invoice.domain.StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            var header = new com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader(account.getId(), com.red_escuela.backend.shared.domain.Currency.ARS,
                    com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType.INVOICE, com.red_escuela.backend.billing.invoice.domain.InvoiceType.ADMINISTRATIVE_FEE,
                    com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason.AD_HOC_CHARGE, null);
            var line = new com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft(com.red_escuela.backend.billing.invoice.domain.FeeType.ADMINISTRATIVE_FEE, "Concepto",
                    java.time.YearMonth.of(2027, 3), null, null, null, null, null, "KR:" + System.nanoTime(),
                    com.red_escuela.backend.shared.domain.NonNegativeMoney.of(new BigDecimal(amount), com.red_escuela.backend.shared.domain.Currency.ARS));
            var actor = com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor.ofProcess("KARATE");
            var draft = com.red_escuela.backend.billing.invoice.domain.InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, actor);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType.INVOICE),
                    java.time.LocalDate.of(2027, 3, 1), java.time.LocalDate.of(2027, 3, 10), snapshot, actor);
            var saved = invoiceRepository.save(draft);
            accountService.recordInvoiceIssuedDebit(account, saved, java.time.LocalDate.of(2027, 3, 1));
            return saved.getId();
        });
    }

    public static Map<String, Object> createProgramLearnerWithPricing() {
        var factory = context().getBean(com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory.class);
        var transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                context().getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var studentRepository = context().getBean(StudentRepository.class);
        var personRepository = context().getBean(PersonRepository.class);
        var roleRepository = context().getBean(RoleRepository.class);
        var jwtService = context().getBean(JwtService.class);
        var learner = factory.newLearner(factory.newProgram(com.red_escuela.backend.shared.domain.Currency.ARS, 80000, 50000), null);
        String accessToken = transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(learner.studentId()).orElseThrow().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            return jwtService.generateAccessToken(personRepository.save(person));
        });
        Map<String, Object> result = new HashMap<>();
        result.put("studentId", learner.studentId());
        result.put("programEnrollmentId", learner.programEnrollmentId());
        result.put("accessToken", accessToken);
        return result;
    }

    public static Long createProgramId() {
        var factory = context().getBean(com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory.class);
        return factory.newProgram(com.red_escuela.backend.shared.domain.Currency.ARS, 80000, 50000).programId();
    }
}
