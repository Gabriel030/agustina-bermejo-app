package com.red_escuela.backend.testsupport.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.identity.service.LocalSeedRunner;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class LoadTestSeedConfiguration {

    private static final String SESSIONS_FILE_VARIABLE = "LOAD_TEST_SESSIONS_FILE";
    private static final String STUDENTS_VARIABLE = "LOAD_TEST_STUDENTS";
    private static final String TEACHERS_VARIABLE = "LOAD_TEST_TEACHERS";
    private static final String ROSTER_SIZE_VARIABLE = "LOAD_TEST_ROSTER_SIZE";
    private static final String INVOICES_VARIABLE = "LOAD_TEST_INVOICES_PER_STUDENT";
    private static final String DEFAULT_SESSIONS_FILE = "target/load-test-sessions.json";
    private static final String INSTALLMENT_AMOUNT = "80000.00";
    private static final int DAYS_UNTIL_DUE = 30;
    private static final int DEFAULT_STUDENTS = 2000;
    private static final int DEFAULT_TEACHERS = 10;
    private static final int DEFAULT_ROSTER_SIZE = 40;
    private static final int DEFAULT_INVOICES_PER_STUDENT = 3;
    private static final int DNI_BASE = 70_000_000;

    @Bean
    ApplicationRunner loadTestSeedRunner(
            LocalSeedRunner demoAccountsSeed,
            EntityManager entityManager,
            RoleRepository roleRepository,
            JwtService jwtService,
            StudentCurrentAccountService accountService,
            InvoiceRepository invoiceRepository,
            InvoiceNumberGenerator numberGenerator,
            TransactionTemplate transactions) {
        return arguments -> {
            demoAccountsSeed.run(arguments);
            new Seeder(entityManager, roleRepository, jwtService, accountService, invoiceRepository, numberGenerator, transactions).seedAndPublish();
        };
    }

    private static int intFromEnvironment(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static final class Seeder {

        private final EntityManager entityManager;
        private final RoleRepository roleRepository;
        private final JwtService jwtService;
        private final StudentCurrentAccountService accountService;
        private final InvoiceRepository invoiceRepository;
        private final InvoiceNumberGenerator numberGenerator;
        private final TransactionTemplate transactions;
        private final int studentCount = intFromEnvironment(STUDENTS_VARIABLE, DEFAULT_STUDENTS);
        private final int teacherCount = intFromEnvironment(TEACHERS_VARIABLE, DEFAULT_TEACHERS);
        private final int rosterSize = intFromEnvironment(ROSTER_SIZE_VARIABLE, DEFAULT_ROSTER_SIZE);
        private final int invoicesPerStudent = intFromEnvironment(INVOICES_VARIABLE, DEFAULT_INVOICES_PER_STUDENT);
        private int nextDni = DNI_BASE;

        Seeder(EntityManager entityManager, RoleRepository roleRepository, JwtService jwtService, StudentCurrentAccountService accountService,
               InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator, TransactionTemplate transactions) {
            this.entityManager = entityManager;
            this.roleRepository = roleRepository;
            this.jwtService = jwtService;
            this.accountService = accountService;
            this.invoiceRepository = invoiceRepository;
            this.numberGenerator = numberGenerator;
            this.transactions = transactions;
        }

        void seedAndPublish() {
            Long programId = transactions.execute(status -> persistProgram());
            Long institutionId = transactions.execute(status -> persistInstitution());
            List<Map<String, Object>> students = new ArrayList<>();
            List<Long> studentIds = new ArrayList<>();
            List<Long> programEnrollmentIds = new ArrayList<>();
            for (int index = 0; index < studentCount; index++) {
                int studentIndex = index;
                Map<String, Object> session = transactions.execute(status -> persistStudent(studentIndex, programId, institutionId));
                students.add(session);
                studentIds.add((Long) session.remove("studentId"));
                programEnrollmentIds.add((Long) session.get("programEnrollmentId"));
            }
            List<Map<String, Object>> teachers = new ArrayList<>();
            for (int index = 0; index < teacherCount; index++) {
                int teacherIndex = index;
                teachers.add(transactions.execute(status -> persistTeacherWithRoster(teacherIndex, studentIds, programEnrollmentIds)));
            }
            Map<String, Object> administrative = transactions.execute(status -> persistAdministrative());
            Map<String, Object> sessions = new LinkedHashMap<>();
            sessions.put("students", students);
            sessions.put("teachers", teachers);
            sessions.put("administrative", administrative);
            publish(sessions);
        }

        private Long persistProgram() {
            Program program = new Program();
            program.setName("Carrera de carga");
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
            return program.getId();
        }

        private Long persistInstitution() {
            Institution institution = new Institution();
            institution.setName("Instituto de carga");
            entityManager.persist(institution);
            return institution.getId();
        }

        private Map<String, Object> persistStudent(int index, Long programId, Long institutionId) {
            Person person = persistPerson("load.student." + index, "ALUMNO");
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(entityManager.getReference(Institution.class, institutionId));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(entityManager.getReference(Program.class, programId));
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            entityManager.flush();
            Long accountId = accountService.findOrOpenAndLockStudentCurrentAccount(student.getId(), Currency.ARS).getId();
            List<Long> invoiceIds = new ArrayList<>();
            for (int installment = 1; installment <= invoicesPerStudent; installment++) {
                invoiceIds.add(issueInstallment(accountId, student.getId(), programEnrollment.getId(), installment));
            }
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("token", jwtService.generateAccessToken(person));
            session.put("programEnrollmentId", programEnrollment.getId());
            session.put("invoiceIds", invoiceIds);
            session.put("studentId", student.getId());
            entityManager.flush();
            entityManager.clear();
            return session;
        }

        private Long issueInstallment(Long accountId, Long studentId, Long programEnrollmentId, int installment) {
            LocalDate today = LocalDate.now();
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Cuota de carga " + installment,
                    YearMonth.from(today).plusMonths(installment), programEnrollmentId, null, null, null, null,
                    "LOAD:" + studentId + ":" + installment, NonNegativeMoney.of(new BigDecimal(INSTALLMENT_AMOUNT), Currency.ARS));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, InvoiceTestData.ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), today, today.plusDays(DAYS_UNTIL_DUE),
                    InvoiceTestData.SNAPSHOT, InvoiceTestData.ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            accountService.recordInvoiceIssuedDebit(accountService.lockStudentCurrentAccount(accountId), saved, today);
            return saved.getId();
        }

        private Map<String, Object> persistTeacherWithRoster(int index, List<Long> studentIds, List<Long> programEnrollmentIds) {
            Person person = persistPerson("load.teacher." + index, "DOCENTE");
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            entityManager.persist(instructor);
            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia de carga " + index);
            entityManager.persist(subject);
            Term term = new Term();
            term.setYear(2026);
            term.setDescription("Carga " + index);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            EvaluationScheme scheme = new EvaluationScheme();
            scheme.setName("Esquema de carga " + TestSubjectCodes.next());
            scheme.setExamCountExpected(2);
            scheme.setTpCountExpected(0);
            scheme.setHasFinal(false);
            scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
            scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
            scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
            entityManager.persist(scheme);
            SubjectSection section = new SubjectSection();
            section.setName("Comision de carga " + index);
            section.setSubject(subject);
            section.setTerm(term);
            section.setEvaluationScheme(scheme);
            section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
            section.setCapacity(rosterSize + 1);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
            for (int offset = 0; offset < rosterSize && !studentIds.isEmpty(); offset++) {
                int position = (index * rosterSize + offset) % studentIds.size();
                SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
                enrollment.setStudent(entityManager.getReference(Student.class, studentIds.get(position)));
                enrollment.setProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, programEnrollmentIds.get(position)));
                enrollment.setSubjectSection(section);
                enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
                enrollment.setEnrolledAt(LocalDateTime.now());
                enrollment.setAttemptNumber(1);
                entityManager.persist(enrollment);
            }
            entityManager.flush();
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("token", jwtService.generateAccessToken(person));
            session.put("sectionId", section.getId());
            entityManager.clear();
            return session;
        }

        private Map<String, Object> persistAdministrative() {
            Person person = persistPerson("load.administrative", "ADMINISTRATIVO");
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("token", jwtService.generateAccessToken(person));
            return session;
        }

        private Person persistPerson(String username, String roleName) {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Carga");
            person.setLastname(username);
            person.setUsername(username);
            person.setDni(String.valueOf(nextDni++));
            person.setEmail(username + "@example.com");
            person.setPassword("not-used");
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return person;
        }

        private void publish(Map<String, Object> sessions) {
            Path target = Path.of(System.getenv().getOrDefault(SESSIONS_FILE_VARIABLE, DEFAULT_SESSIONS_FILE));
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                Files.createDirectories(target.toAbsolutePath().getParent());
                new ObjectMapper().writeValue(temporary.toFile(), sessions);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
