package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.graduation.entity.CertificateIssuance;
import com.red_escuela.backend.graduation.entity.CertificateType;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.CertificateIssuanceRepository;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.PersonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("local & demo")
public class DemoEdgeCaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoEdgeCaseSeeder.class);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final String PASSWORD = "Test1234";

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final InstructorRepository instructorRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final GradeItemRepository gradeItemRepository;
    private final GraduationRequestRepository graduationRequestRepository;
    private final CertificateIssuanceRepository certificateIssuanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;

    public DemoEdgeCaseSeeder(
            PersonRepository personRepository, RoleRepository roleRepository, StudentRepository studentRepository,
            InstructorRepository instructorRepository, ProgramRepository programRepository,
            CourseRepository courseRepository, SubjectSectionRepository subjectSectionRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            CourseEnrollmentRepository courseEnrollmentRepository, ExamBoardRepository examBoardRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository, GradeItemRepository gradeItemRepository,
            GraduationRequestRepository graduationRequestRepository,
            CertificateIssuanceRepository certificateIssuanceRepository, InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository, CreditNoteRepository creditNoteRepository,
            PersonFactory personFactory, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.instructorRepository = instructorRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.gradeItemRepository = gradeItemRepository;
        this.graduationRequestRepository = graduationRequestRepository;
        this.certificateIssuanceRepository = certificateIssuanceRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed(DemoDataset dataset) {
        createApplicantWithoutRole(dataset);
        createMultiRoleStaff(dataset);
        createIdleInstructor();
        createStudentWithTwoPrograms(dataset);
        createWaitlistedStudent(dataset);
        createGraduatedStudent(dataset);
        createEmptyProgram();
        createFullSection(dataset);
        createPartialPayment(dataset);
        createAppliedCreditNote(dataset);
        createLongNamedStudent(dataset);
        log.info("Seed demo: casos borde cargados");
    }

    private Person createLogin(String firstName, String lastName, String username, String dni, List<String> roleNames) {
        Person person = personFactory.createBasePerson(firstName, lastName, username + "@isab.edu.ar");
        person.setUsername(username);
        person.setPassword(passwordEncoder.encode(PASSWORD));
        person.setDni(dni);
        person.setStatus(StatusType.ACTIVE);
        person.setGrantSystemAccess(true);
        person.setEmailVerified(true);
        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Falta el rol " + roleName));
            person.getRoles().add(role);
        }
        return personRepository.save(person);
    }

    private void createApplicantWithoutRole(DemoDataset dataset) {
        Person person = createLogin("Nahuel", "Pereyra", "aspirante.demo", "40111222", List.of());
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(dataset.getInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        studentRepository.save(student);
    }

    private void createMultiRoleStaff(DemoDataset dataset) {
        Person person = createLogin("Fabián", "Luque", "multirol.demo", "40111333", List.of("DOCENTE", "PRECEPTOR"));
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        instructorRepository.save(instructor);
    }

    private void createIdleInstructor() {
        Person person = createLogin("Elena", "Sarmiento", "docente.sincomisiones", "40111444", List.of("DOCENTE"));
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        instructorRepository.save(instructor);
    }

    private void createStudentWithTwoPrograms(DemoDataset dataset) {
        Student student = dataset.getDemoStudent();
        ProgramEnrollment second = new ProgramEnrollment();
        second.setStudent(student);
        second.setProgram(dataset.program(DemoCatalog.EDUCATION_MANAGEMENT));
        second.setStatus(ProgramEnrollmentStatus.ENROLLED);
        second.setEnrollmentDate(LocalDate.of(2026, 3, 5).atStartOfDay());
        second.setBillingStanding(EnrollmentBillingStanding.UP_TO_DATE);
        second.setBillingStandingUpdatedAt(TODAY.atStartOfDay());
        programEnrollmentRepository.save(second);
    }

    private void createWaitlistedStudent(DemoDataset dataset) {
        Course course = dataset.course("Primeros Auxilios y RCP");
        long inscriptos = courseEnrollmentRepository.findAll().stream()
                .filter(e -> e.getCourse().getId().equals(course.getId()))
                .count();
        course.setCapacity((int) inscriptos);
        courseRepository.save(course);

        Person person = createLogin("Rocío", "Maldonado", "espera.demo", "40111555", List.of("ALUMNO"));
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(dataset.getInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        Student saved = studentRepository.save(student);

        CourseEnrollment waitlisted = new CourseEnrollment();
        waitlisted.setStudent(saved);
        waitlisted.setCourse(course);
        waitlisted.setStatus(EnrollmentStatus.WAITLISTED);
        waitlisted.setEnrollmentDate(TODAY.minusDays(4).atStartOfDay());
        courseEnrollmentRepository.save(waitlisted);
    }

    private void createGraduatedStudent(DemoDataset dataset) {
        List<ProgramEnrollment> completadas = programEnrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == ProgramEnrollmentStatus.COMPLETED)
                .toList();
        if (completadas.isEmpty()) {
            return;
        }
        ProgramEnrollment enrollment = completadas.get(0);
        GraduationRequest request = graduationRequestRepository.findAll().stream()
                .filter(r -> r.getProgramEnrollment().getId().equals(enrollment.getId()))
                .findFirst()
                .orElseGet(() -> {
                    GraduationRequest nueva = new GraduationRequest();
                    nueva.setProgramEnrollment(enrollment);
                    nueva.setRequestedAt(TODAY.minusDays(45).atTime(10, 0));
                    return nueva;
                });
        request.setStatus(GraduationRequestStatus.APPROVED);
        request.setVerifiedBy(dataset.administrativeAt(0));
        request.setVerifiedAt(TODAY.minusDays(43).atTime(10, 0));
        request.setDecidedBy(dataset.getDirector());
        request.setDecidedAt(TODAY.minusDays(40).atTime(15, 0));
        graduationRequestRepository.save(request);

        CertificateIssuance certificate = new CertificateIssuance();
        certificate.setType(CertificateType.APPROVED_SUBJECTS);
        certificate.setProgramEnrollment(enrollment);
        certificate.setIssuedAt(TODAY.minusDays(39).atTime(9, 30));
        certificate.setRequestedBy(enrollment.getStudent().getPerson());
        certificateIssuanceRepository.save(certificate);
    }

    private void createEmptyProgram() {
        Program program = new Program();
        program.setName("Tecnicatura Superior en Turismo y Hotelería");
        program.setStatus(StatusType.ACTIVE);
        program.setPrice(new BigDecimal("1250000"));
        program.setEnrollmentFeeAmount(new BigDecimal("2400000"));
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        program.setVisibleInCatalog(true);
        program.setTeachingMode(TeachingMode.HYBRID);
        program.setLevel(ProgramLevel.UNDERGRADUATE);
        program.setGraduationProfile("Carrera en armado: el plan de estudios todavía no fue cargado.");
        programRepository.save(program);
    }

    private void createFullSection(DemoDataset dataset) {
        SubjectSection section = dataset.section(DemoCatalog.EARLY_CHILDHOOD, "Pedagogía", 2026);
        if (section == null) {
            return;
        }
        long inscriptos = dataset.getSectionEnrollments().stream()
                .filter(e -> e.getSubjectSection().getId().equals(section.getId()))
                .count();
        section.setCapacity((int) Math.max(inscriptos, 1));
        subjectSectionRepository.save(section);
    }

    private void createPartialPayment(DemoDataset dataset) {
        List<Invoice> pendientes = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PENDING)
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(1)
                .toList();
        if (pendientes.isEmpty()) {
            return;
        }
        Invoice invoice = pendientes.get(0);
        BigDecimal mitad = invoice.getAmountDue().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        Payment payment = new Payment();
        payment.setAmount(mitad);
        payment.setMethod(PaymentMethod.CASH);
        payment.setRegisteredBy(dataset.administrativeAt(0));
        payment.setPaidAt(TODAY.minusDays(2).atTime(12, 0));
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setNotes("Pago parcial acordado en mostrador");
        Payment savedPayment = paymentRepository.save(payment);
        invoice.registerPayment(mitad, savedPayment);
        invoiceRepository.save(invoice);
    }

    private void createAppliedCreditNote(DemoDataset dataset) {
        List<Invoice> pendientes = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PENDING)
                .filter(i -> i.getProgramEnrollment() != null)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .limit(1)
                .toList();
        if (pendientes.isEmpty()) {
            return;
        }
        Invoice invoice = pendientes.get(0);
        CreditNote note = new CreditNote();
        note.setStudent(invoice.getProgramEnrollment().getStudent());
        note.setAmount(new BigDecimal("30000"));
        note.setReasonType(CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP);
        note.setReason("Beca otorgada con efecto retroactivo al inicio del ciclo");
        note.setCreatedBy(dataset.getDirector());
        CreditNote savedNote = creditNoteRepository.save(note);
        savedNote.consume(new BigDecimal("30000"), invoice, null);
        creditNoteRepository.save(savedNote);
    }

    private void createLongNamedStudent(DemoDataset dataset) {
        Person person = createLogin(
                "María de los Ángeles Guadalupe", "Fernández Iturralde de Peñaloza",
                "nombrelargo.demo", "40111666", List.of("ALUMNO"));
        person.setPhoneNumber("+54 9 351 555-0199");
        personRepository.save(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(dataset.getInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        Student saved = studentRepository.save(student);
        saved.setWithdrawalReason(WithdrawalReason.CAMBIO_CARRERA);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(saved);
        enrollment.setProgram(dataset.program(DemoCatalog.NURSING));
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 3, 5).atStartOfDay());
        enrollment.setBillingStanding(EnrollmentBillingStanding.SUSPENDED);
        enrollment.setBillingStandingUpdatedAt(TODAY.atStartOfDay());
        programEnrollmentRepository.save(enrollment);
    }
}
