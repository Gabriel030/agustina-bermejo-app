package com.red_escuela.backend.security;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class OwnershipFixtures {

    private final EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    private String ownerStudentToken;
    private String strangerStudentToken;
    private String instructorToken;
    private String strangerInstructorToken;
    private Long programEnrollmentId;
    private Long sectionEnrollmentId;
    private Long courseEnrollmentId;
    private Long courseSectionEnrollmentId;
    private Long sectionId;
    private Long attendanceId;
    private Long annualUpfrontRequestId;
    private Long instructorPersonId;
    private Long examBoardId;
    private Long invoiceId;
    private Long notificationId;
    private Long sessionId;
    private Long courseSectionId;
    private Long programPlanOptionId;
    private Long coursePlanOptionId;
    private Long closureId;
    private Long closureSectionId;
    private String teacherDirectorToken;
    private String teacherStudentToken;

    OwnershipFixtures(TransactionTemplate transactions, EntityManager entityManager, RoleRepository roleRepository,
                      JwtService jwtService) {
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.jwtService = jwtService;
        transactions.executeWithoutResult(status -> persistScenario());
    }

    Long resourceIdFor(OwnershipKind kind) {
        return switch (kind) {
            case PROGRAM_ENROLLMENT -> programEnrollmentId;
            case SECTION_ENROLLMENT -> sectionEnrollmentId;
            case COURSE_ENROLLMENT -> courseEnrollmentId;
            case COURSE_SECTION_ENROLLMENT -> courseSectionEnrollmentId;
            case SECTION_INSTRUCTOR -> sectionId;
            case ATTENDANCE_SECTION_INSTRUCTOR -> attendanceId;
            case ANNUAL_UPFRONT_REQUEST -> annualUpfrontRequestId;
            case STAFF_RECORD -> instructorPersonId;
            case EXAM_BOARD_TRIBUNAL -> examBoardId;
            case INVOICE -> invoiceId;
            case NOTIFICATION -> notificationId;
            case SESSION -> sessionId;
            case SECTION_ENROLLMENT_INSTRUCTOR -> sectionEnrollmentId;
            case COURSE_SECTION_ENROLLMENT_INSTRUCTOR -> courseSectionEnrollmentId;
            case COURSE_SECTION_INSTRUCTOR -> courseSectionId;
        };
    }

    String ownerTokenFor(OwnershipKind kind) {
        return kind.ownerRole() == MatrixRole.ALUMNO ? ownerStudentToken : instructorToken;
    }

    String strangerTokenFor(OwnershipKind kind) {
        return kind.ownerRole() == MatrixRole.ALUMNO ? strangerStudentToken : strangerInstructorToken;
    }

    Long closureId() {
        return closureId;
    }

    Long examBoardId() {
        return examBoardId;
    }

    Long closureSectionId() {
        return closureSectionId;
    }

    String teacherDirectorToken() {
        return teacherDirectorToken;
    }

    String teacherStudentToken() {
        return teacherStudentToken;
    }

    private void persistScenario() {
        Person ownerPerson = persistPerson("owner.student", "91000001", "ALUMNO");
        Person strangerPerson = persistPerson("stranger.student", "91000002", "ALUMNO");
        Person instructorPerson = persistPerson("owner.teacher", "91000003", "DOCENTE");
        Person strangerInstructorPerson = persistPerson("stranger.teacher", "91000004", "DOCENTE");
        Person teacherDirectorPerson = persistPerson("multirole.teacher.director", "91000005", "DOCENTE", "DIRECTOR");
        Person teacherStudentPerson = persistPerson("multirole.teacher.student", "91000006", "DOCENTE", "ALUMNO");
        persistInstructor(teacherDirectorPerson);
        persistInstructor(teacherStudentPerson);
        Student ownerStudent = persistStudent(ownerPerson);
        persistStudent(strangerPerson);
        Instructor instructor = persistInstructor(instructorPerson);
        persistInstructor(strangerInstructorPerson);

        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia matriz");
        entityManager.persist(subject);
        Program program = new Program();
        program.setName("Carrera matriz");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramSubject planEntry = new ProgramSubject();
        planEntry.setProgram(program);
        planEntry.setSubject(subject);
        planEntry.setCredits(10);
        planEntry.setCurricularYear(1);
        planEntry.setCurricularTerm(CurricularTerm.FIRST);
        planEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(planEntry);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(false);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);

        SubjectSection section = new SubjectSection();
        section.setName("Comision matriz");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setCapacity(30);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);

        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(ownerStudent);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(ownerStudent);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        Attendance absence = new Attendance();
        absence.setProgramSubjectSectionEnrollment(enrollment);
        absence.setClassDate(LocalDate.of(2026, 3, 2));
        absence.setStatus(AttendanceStatus.ABSENT);
        absence.setRecordedBy(instructor);
        entityManager.persist(absence);
        entityManager.flush();
        attendanceId = absence.getId();
        examBoardId = persistExamBoard(subject, term, instructor);
        closureSectionId = persistClosureSection(subject, term, scheme, instructor);
        closureId = persistClosure(closureSectionId);
        courseSectionEnrollmentId = persistCourseSectionEnrollment(ownerStudent, term, instructor);
        annualUpfrontRequestId = persistAnnualUpfrontRequestAwaitingPayment(ownerStudent, ownerPerson, programEnrollment, term);
        programPlanOptionId = persistPlanOption(ownerPerson, program, null);
        coursePlanOptionId = persistPlanOption(ownerPerson, null, courseOfEnrollment());
        notificationId = persistNotification(ownerPerson);
        sessionId = persistRefreshToken(ownerPerson);

        ownerStudentToken = jwtService.generateAccessToken(ownerPerson);
        strangerStudentToken = jwtService.generateAccessToken(strangerPerson);
        teacherDirectorToken = jwtService.generateAccessToken(teacherDirectorPerson);
        teacherStudentToken = jwtService.generateAccessToken(teacherStudentPerson);
        instructorToken = jwtService.generateAccessToken(instructorPerson);
        strangerInstructorToken = jwtService.generateAccessToken(strangerInstructorPerson);
        programEnrollmentId = programEnrollment.getId();
        sectionEnrollmentId = enrollment.getId();
        sectionId = section.getId();
        instructorPersonId = instructorPerson.getId();
    }

    private Long persistExamBoard(Subject subject, Term term, Instructor tribunalMember) {
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(LocalDateTime.now().plusDays(30));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        ExamBoardInstructor member = new ExamBoardInstructor();
        member.setExamBoard(board);
        member.setInstructor(tribunalMember);
        board.setTribunal(List.of(member));
        entityManager.persist(board);
        entityManager.flush();
        return board.getId();
    }

    private Long persistClosureSection(Subject subject, Term term, EvaluationScheme scheme, Instructor instructor) {
        SubjectSection section = new SubjectSection();
        section.setName("Comision cierre matriz");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setCapacity(30);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        entityManager.flush();
        return section.getId();
    }

    private Long persistClosure(Long sectionId) {
        CommissionClosure closure = CommissionClosure.builder()
                .sectionKind(SectionKind.PROGRAM)
                .sectionId(sectionId)
                .status(ClosureStatus.DRAFT_AVAILABLE)
                .build();
        entityManager.persist(closure);
        entityManager.flush();
        return closure.getId();
    }

    private EvaluationScheme persistScheme() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(false);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        return scheme;
    }

    private Long persistCourseSectionEnrollment(Student student, Term term, Instructor instructor) {
        Course course = new Course();
        course.setName("Curso matriz");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName("Modulo matriz");
        entityManager.persist(courseSubject);
        CourseSubjectSection courseSection = new CourseSubjectSection();
        courseSection.setName("Comision curso matriz");
        courseSection.setCourseSubject(courseSubject);
        courseSection.setTerm(term);
        courseSection.setEvaluationScheme(persistScheme());
        courseSection.setStatus(StatusType.ACTIVE);
        entityManager.persist(courseSection);
        CourseSubjectSectionInstructor courseAssignment = new CourseSubjectSectionInstructor();
        courseAssignment.setSection(courseSection);
        courseAssignment.setInstructor(instructor);
        courseAssignment.setRole(InstructorRole.LEAD);
        entityManager.persist(courseAssignment);
        courseSectionId = courseSection.getId();
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        courseEnrollmentId = courseEnrollment.getId();
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(courseSection);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
        return enrollment.getId();
    }

    private Long persistAnnualUpfrontRequestAwaitingPayment(Student student, Person person, ProgramEnrollment programEnrollment, Term term) {
        Long accountId = ((Number) entityManager.createNativeQuery("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (:studentId, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """).setParameter("studentId", student.getId()).getSingleResult()).longValue();
        Long invoiceId = ((Number) entityManager.createNativeQuery("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status, payment_deadline,
                    lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (:accountId, 'ARS', 'INVOICE', 'ANNUAL_UPFRONT', 'ANNUAL_UPFRONT_PAYMENT', 'AWAITING_PAYMENT', now() + interval '2 days',
                    100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now())
                returning id
                """).setParameter("accountId", accountId).getSingleResult()).longValue();
        this.invoiceId = invoiceId;
        return ((Number) entityManager.createNativeQuery("""
                insert into annual_upfront_request (program_enrollment_id, lective_year_term_id, invoice_id, status, requested_by_person_id, requested_at,
                    accepted_terms_version, accepted_terms_text, accepted_at, accepted_by_person_id)
                values (:programEnrollmentId, :termId, :invoiceId, 'AWAITING_PAYMENT', :personId, now(), 'v1', 'Términos de prueba', now(), :personId)
                returning id
                """).setParameter("programEnrollmentId", programEnrollment.getId()).setParameter("termId", term.getId()).setParameter("invoiceId", invoiceId)
                .setParameter("personId", person.getId()).getSingleResult()).longValue();
    }

    String resolvePlaceholders(String body) {
        return body.replace("111111", String.valueOf(programPlanOptionId))
                .replace("222222", String.valueOf(coursePlanOptionId));
    }

    private Course courseOfEnrollment() {
        return entityManager.find(CourseEnrollment.class, courseEnrollmentId).getCourse();
    }

    private Long persistPlanOption(Person creator, Program program, Course course) {
        PaymentPlanOption option = new PaymentPlanOption();
        option.setProgram(program);
        option.setCourse(course);
        option.setDescription("Opcion matriz");
        option.setCadence(PaymentPlanCadence.MONTHLY);
        option.setCreatedBy(creator);
        entityManager.persist(option);
        entityManager.flush();
        return option.getId();
    }

    private Long persistNotification(Person recipient) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(NotificationType.SECTION_EVAL_CONFIG_REMINDER);
        notification.setMessage("Notificacion matriz");
        notification.setCreatedAt(LocalDateTime.now());
        entityManager.persist(notification);
        entityManager.flush();
        return notification.getId();
    }

    private Long persistRefreshToken(Person person) {
        RefreshToken token = new RefreshToken();
        token.setPerson(person);
        token.setTokenHash("matrix-session-" + person.getId());
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        entityManager.persist(token);
        entityManager.flush();
        return token.getId();
    }

    private Person persistPerson(String username, String dni, String roleName, String... extraRoleNames) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Matriz");
        person.setLastname(username);
        person.setUsername(username);
        person.setDni(dni);
        person.setEmail(username + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        for (String extraRoleName : extraRoleNames) {
            person.getRoles().add(roleRepository.findByName(extraRoleName).orElseThrow());
        }
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private Student persistStudent(Person person) {
        Institution institution = new Institution();
        institution.setName("Instituto " + person.getDni());
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }
}
