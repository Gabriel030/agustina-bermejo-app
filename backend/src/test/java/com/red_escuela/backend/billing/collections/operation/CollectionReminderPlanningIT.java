package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.collections.domain.CollectionReminder;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRepository;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTarget;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTargetFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class CollectionReminderPlanningIT extends AbstractIntegrationTest {

    private static final LocalDate MARCH_FIRST = LocalDate.of(2027, 3, 1);
    private static final LocalDate MARCH_ELEVENTH = LocalDate.of(2027, 3, 11);

    @Autowired
    private CollectionReminderPlanningStep planningStep;
    @Autowired
    private CollectionReminderRepository reminderRepository;
    @Autowired
    private ProposeBillingConfigurationChangeUseCase proposeChange;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private EnrollmentReminderTargetFinder targetLookup;

    private Institution institution;
    private Term term;
    private Person director;

    @BeforeEach
    void setUp() {
        com.red_escuela.backend.shared.time.ApplicationTime.useClock(MutableClock.INSTANCE);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 26, 12, 0));
        institution = new Institution();
        institution.setName("Instituto recordatorios " + System.nanoTime());
        institution = institutionRepository.save(institution);
        term = new Term();
        term.setYear(2027 + (int) (System.nanoTime() % 1000));
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        director = newPerson("director", "DIRECTOR");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private Person newPerson(String prefix, String role) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Recordatorio");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            if (role != null) {
                person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            }
            return personRepository.save(person);
        });
    }

    private Student newStudent() {
        Student student = new Student();
        student.setPerson(newPerson("alumno", null));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Course newCourse() {
        Course course = new Course();
        course.setName("Curso recordatorio " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course);
    }

    private CourseEnrollment enrollInCourse(Student student, Course course) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        return courseEnrollmentRepository.save(enrollment);
    }

    private ProgramEnrollment enrollInProgram(Student student) {
        Program program = new Program();
        program.setName("Carrera recordatorio " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programRepository.save(program));
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        return programEnrollmentRepository.save(enrollment);
    }

    private Invoice installmentOf(CourseEnrollment enrollment, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).build();
        return withDates(invoice, issueDate, dueDate);
    }

    private Invoice installmentOf(ProgramEnrollment enrollment, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).build();
        return withDates(invoice, issueDate, dueDate);
    }

    private Invoice withDates(Invoice invoice, LocalDate issueDate, LocalDate dueDate) {
        jdbc.update("update invoice set issue_date = ?, due_date = ? where id = ?", issueDate, dueDate, invoice.getId());
        return invoice;
    }

    private CollectionReminderPlanningResult planOn(LocalDate date, Student... students) {
        MutableClock.INSTANCE.setDateTime(date.atTime(5, 0));
        return planningStep.planCollectionRemindersOf(date, java.util.Arrays.stream(students).map(Student::getId).toList());
    }

    private List<CollectionReminderPlannedEvent> eventsOf(Student student) {
        Long personId = student.getPerson().getId();
        return applicationEvents.stream(CollectionReminderPlannedEvent.class).filter(event -> event.studentPersonId().equals(personId)).toList();
    }

    private ReminderNotice inAppNotice(String key, ReminderMilestone milestone, int offsetDays, ReminderRepetition repetition) {
        return new ReminderNotice(key, ReminderMessageType.AFTER_DUE_REMINDER, milestone, offsetDays, EnumSet.of(ReminderChannel.IN_APP), repetition);
    }

    private void publishCourseSchedule(Long courseId, LocalDate effectiveFrom, ReminderNotice... notices) {
        proposeChange.proposeChange(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.MONTHLY_COURSES, courseId),
                new CourseReminderSchedule(new ReminderSchedule(false, List.of(notices), ReminderSendingOverrides.none())), effectiveFrom, "motivo");
    }

    @Test
    @Tag("D-353")
    void planningTwiceTheSameDayKeepsOneReminderAndOneEvent() {
        Student student = newStudent();
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);

        CollectionReminderPlanningResult first = planOn(MARCH_FIRST, student);
        CollectionReminderPlanningResult second = planOn(MARCH_FIRST, student);

        assertThat(first.planned()).isEqualTo(1);
        assertThat(second.planned()).isZero();
        assertThat(second.alreadyPlanned()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from collection_reminder where student_id = ?", Long.class, student.getId())).isEqualTo(1L);
        assertThat(eventsOf(student)).hasSize(1);
        assertThat(eventsOf(student).get(0).emailMessages()).isEmpty();
        assertThat(eventsOf(student).get(0).inAppMessages()).hasSize(1);
    }

    @Test
    @Tag("D-353")
    @Tag("D-097")
    @Tag("D-410")
    void oneRowPerStudentAcrossCurrencies() {
        Student student = newStudent();
        CourseEnrollment pesos = enrollInCourse(student, newCourse());
        CourseEnrollment dollars = enrollInCourse(student, newCourse());
        installmentOf(pesos, MARCH_FIRST, MARCH_ELEVENTH);
        withDates(BillingTestFixtures.invoice().forCourseEnrollment(dollars).forPeriod(YearMonth.of(2027, 3)).dueOn(MARCH_ELEVENTH).withBaseAmount(usd(100)).build(),
                MARCH_FIRST, MARCH_ELEVENTH);

        planOn(MARCH_FIRST, student);

        assertThat(jdbc.queryForObject("select count(*) from collection_reminder where student_id = ?", Long.class, student.getId())).isEqualTo(1L);
        PlannedReminderMessage message = eventsOf(student).get(0).inAppMessages().get(0);
        assertThat(message.blocks()).hasSize(2);
        assertThat(message.blocks()).extracting(block -> block.currency().name()).containsExactlyInAnyOrder("ARS", "USD");
    }

    @Test
    @Tag("D-346")
    @Tag("D-247")
    @Tag("D-354")
    void suppressedInvoicesKeepTheirReason() {
        Student student = newStudent();
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        Invoice plain = installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        Invoice withReceipt = installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        Invoice withCancellation = installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        Invoice examFee = withDates(BillingTestFixtures.invoice().forStudent(student).ofType(FeeType.EXAM_FEE).dueOn(MARCH_ELEVENTH).withBaseAmount(ars(5000)).build(),
                MARCH_FIRST, MARCH_ELEVENTH);
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'comprobante.pdf', timestamp '2099-01-01 10:00:00', 'PENDING_REVIEW', 0)",
                withReceipt.getId());
        Long requestId = jdbc.queryForObject("insert into authorization_request (operation_type, operation_payload, required_role, reason, status, requested_by_id, requested_at) "
                + "values ('CANCEL_INVOICE', '{}'::jsonb, 'DIRECTOR', 'Baja', 'PENDING', ?, now()) returning id", Long.class, director.getId());
        jdbc.update("insert into authorization_request_invoice (authorization_request_id, invoice_id) values (?, ?)", requestId, withCancellation.getId());

        planOn(MARCH_FIRST, student);

        CollectionReminder reminder = reminderRepository.findByStudentIdAndBusinessDate(student.getId(), MARCH_FIRST).orElseThrow();
        assertThat(reminder.getSuppressed()).extracting(suppressed -> suppressed.invoiceId(), suppressed -> suppressed.reasonCode())
                .containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple(withReceipt.getId(), "RECEIPT_UNDER_REVIEW"),
                        org.assertj.core.api.Assertions.tuple(withCancellation.getId(), "PENDING_CANCELLATION_REQUEST"));
        List<Long> listedInvoiceIds = eventsOf(student).get(0).inAppMessages().get(0).blocks().stream().flatMap(block -> block.lines().stream())
                .map(line -> line.invoiceId()).toList();
        assertThat(listedInvoiceIds).contains(plain.getId(), withReceipt.getId()).doesNotContain(withCancellation.getId(), examFee.getId());
    }

    @Test
    @Tag("D-368")
    @Tag("D-369")
    void courseInvoiceResolvesTheCourseExceptionAndProgramInvoiceTheDefaultSchedule() {
        Student student = newStudent();
        Course course = newCourse();
        CourseEnrollment courseEnrollment = enrollInCourse(student, course);
        ProgramEnrollment programEnrollment = enrollInProgram(student);
        installmentOf(courseEnrollment, MARCH_FIRST, MARCH_ELEVENTH);
        installmentOf(programEnrollment, MARCH_FIRST, MARCH_ELEVENTH);
        publishCourseSchedule(course.getId(), LocalDate.of(2027, 2, 27), inAppNotice("two-days-after-issue", ReminderMilestone.ISSUANCE, 2, null));

        planOn(MARCH_FIRST, student);
        List<PlannedReminderMessage> onTheIssueDay = eventsOf(student).get(0).inAppMessages();
        planOn(LocalDate.of(2027, 3, 3), student);
        List<PlannedReminderMessage> twoDaysLater = eventsOf(student).get(1).inAppMessages();

        assertThat(onTheIssueDay).hasSize(1);
        assertThat(onTheIssueDay.get(0).blocks().get(0).lines().stream().filter(line -> line.isHighlighted()).count()).isEqualTo(1);
        assertThat(twoDaysLater).hasSize(1);
        assertThat(twoDaysLater.get(0).blocks().get(0).lines().stream().filter(line -> line.isHighlighted()).count()).isEqualTo(1);
    }

    @Test
    @Tag("D-413")
    void repetitionUntilSuspensionStopsOnceTheEnrollmentIsSuspended() {
        Student student = newStudent();
        Course course = newCourse();
        CourseEnrollment enrollment = enrollInCourse(student, course);
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        publishCourseSchedule(course.getId(), LocalDate.of(2027, 2, 27), inAppNotice("every-three-days", ReminderMilestone.DUE_DATE, 1,
                new ReminderRepetition(3, ReminderMilestone.SUSPENSION)));

        planOn(LocalDate.of(2027, 3, 12), student);
        planOn(LocalDate.of(2027, 3, 15), student);
        PaymentStandingSeed.seedCourseEnrollmentStanding(jdbc, enrollment.getId(), EnrollmentPaymentStatus.SUSPENDED);
        CollectionReminderPlanningResult afterSuspension = planOn(LocalDate.of(2027, 3, 18), student);

        assertThat(eventsOf(student)).hasSize(2);
        assertThat(afterSuspension.planned()).isZero();
        assertThat(afterSuspension.nothingToSend()).isEqualTo(1);
    }

    @Test
    @Tag("D-413")
    void anInvoiceIssuedAfterThePreviousRunGetsItsIssuanceNoticeTheNextDayAndOneIssuedBeforeDoesNot() {
        Student late = newStudent();
        Student early = newStudent();
        CourseEnrollment lateEnrollment = enrollInCourse(late, newCourse());
        CourseEnrollment earlyEnrollment = enrollInCourse(early, newCourse());
        MutableClock.INSTANCE.setDateTime(MARCH_FIRST.atTime(3, 0));
        installmentOf(earlyEnrollment, MARCH_FIRST, MARCH_ELEVENTH);
        planOn(MARCH_FIRST, early);
        MutableClock.INSTANCE.setDateTime(MARCH_FIRST.atTime(6, 0));
        installmentOf(lateEnrollment, MARCH_FIRST, MARCH_ELEVENTH);

        CollectionReminderPlanningResult theNextDay = planOn(MARCH_FIRST.plusDays(1), late, early);

        assertThat(theNextDay.planned()).isEqualTo(1);
        assertThat(theNextDay.nothingToSend()).isEqualTo(1);
        assertThat(eventsOf(early)).hasSize(1);
        assertThat(eventsOf(late)).hasSize(1);
        assertThat(eventsOf(late).get(0).businessDate()).isEqualTo(MARCH_FIRST.plusDays(1));
    }

    @Test
    @Tag("D-411")
    void withTheDailyDigestDisabledEveryNoticeGoesOutAsItsOwnMessage() {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();
        proposeChange.proposeChange(ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS), new CollectionReminderSettings(initial.suspensionNoticeEnabled(),
                initial.postWithdrawalSummaryMonths(), initial.emailWindowStart(), initial.emailWindowEnd(), initial.dailyEmailCap(), initial.contactListDaysOverdue(),
                initial.contactListOverdueInstallments(), false), LocalDate.of(2027, 2, 27), "Sin resumen diario");
        Student separate = newStudent();
        CourseEnrollment enrollment = enrollInCourse(separate, newCourse());
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);

        planOn(MARCH_FIRST, separate);

        List<PlannedReminderMessage> messages = eventsOf(separate).get(0).inAppMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(PlannedReminderMessage::format).containsOnly(PlannedReminderFormat.OWN_NOTICE);
    }

    @Test
    @Tag("D-411")
    void withTheDailyDigestEnabledByDefaultTwoNoticesOfTheSameDayShareOneMessage() {
        Student student = newStudent();
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);
        installmentOf(enrollment, MARCH_FIRST, MARCH_ELEVENTH);

        planOn(MARCH_FIRST, student);

        List<PlannedReminderMessage> messages = eventsOf(student).get(0).inAppMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).format()).isEqualTo(PlannedReminderFormat.DAILY_DIGEST);
    }

    @Test
    @Tag("D-353")
    void aStudentWhosePlanningFailsDoesNotStopTheSweepOfTheOthers() {
        Student withoutEnrollment = newStudent();
        withDates(BillingTestFixtures.invoice().forStudent(withoutEnrollment).forPeriod(YearMonth.of(2027, 3)).dueOn(MARCH_ELEVENTH).withBaseAmount(ars(100000)).build(),
                MARCH_FIRST, MARCH_ELEVENTH);
        Student healthy = newStudent();
        installmentOf(enrollInCourse(healthy, newCourse()), MARCH_FIRST, MARCH_ELEVENTH);

        CollectionReminderPlanningResult result = planOn(MARCH_FIRST, withoutEnrollment, healthy);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.planned()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).studentId()).isEqualTo(withoutEnrollment.getId());
        assertThat(reminderRepository.existsByStudentIdAndBusinessDate(healthy.getId(), MARCH_FIRST)).isTrue();
        assertThat(reminderRepository.existsByStudentIdAndBusinessDate(withoutEnrollment.getId(), MARCH_FIRST)).isFalse();
    }

    @Test
    @Tag("D-353")
    void twoConcurrentRunsOfTheSameDayLeaveOneReminderPerStudent() throws Exception {
        List<Student> students = List.of(newStudent(), newStudent(), newStudent());
        students.forEach(student -> installmentOf(enrollInCourse(student, newCourse()), MARCH_FIRST, MARCH_ELEVENTH));
        MutableClock.INSTANCE.setDateTime(MARCH_FIRST.atTime(5, 0));
        List<Long> ids = students.stream().map(Student::getId).toList();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<CollectionReminderPlanningResult>> runs = List.of(
                    executor.submit(() -> {
                        start.await();
                        return planningStep.planCollectionRemindersOf(MARCH_FIRST, ids);
                    }),
                    executor.submit(() -> {
                        start.await();
                        return planningStep.planCollectionRemindersOf(MARCH_FIRST, ids);
                    }));
            start.countDown();
            int planned = 0;
            int failed = 0;
            for (java.util.concurrent.Future<CollectionReminderPlanningResult> run : runs) {
                planned += run.get().planned();
                failed += run.get().failed();
            }
            assertThat(planned).isEqualTo(3);
            assertThat(failed).isZero();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from collection_reminder where student_id in (?, ?, ?)", Long.class, ids.get(0), ids.get(1), ids.get(2))).isEqualTo(3L);
        assertThat(applicationEvents.stream(CollectionReminderPlannedEvent.class).count()).isEqualTo(3L);
    }

    @Test
    @Tag("D-353")
    void aRealDuplicateOfStudentAndDayIsReportedByTheDatabaseWithTheNameOfItsUniqueConstraint() {
        Student student = newStudent();
        PlannedReminderMessage message = new PlannedReminderMessage(PlannedReminderFormat.OWN_NOTICE, com.red_escuela.backend.billing.events.PlannedReminderNoticeType.ISSUANCE_NOTICE, 1,
                false, List.of(), null);
        reminderRepository.saveAndFlush(CollectionReminder.recordPlannedFor(student.getId(), MARCH_FIRST, List.of(message), List.of(), List.of()));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> reminderRepository.saveAndFlush(
                CollectionReminder.recordPlannedFor(student.getId(), MARCH_FIRST, List.of(message), List.of(), List.of())));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(thrown).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(constraintNamesOf(thrown)).contains("uq_collection_reminder_student_day");
    }

    private static List<String> constraintNamesOf(Throwable thrown) {
        List<String> names = new java.util.ArrayList<>();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                names.add(violation.getConstraintName());
            }
        }
        return names;
    }

    @Test
    @Tag("D-353")
    void storedMessagesReadBackWithTheirFormatPriorityAndDueDateFlag() {
        Student student = newStudent();
        installmentOf(enrollInCourse(student, newCourse()), MARCH_FIRST, MARCH_ELEVENTH);

        planOn(MARCH_FIRST, student);

        CollectionReminder stored = reminderRepository.findByStudentIdAndBusinessDate(student.getId(), MARCH_FIRST).orElseThrow();
        PlannedReminderMessage fromTheEvent = eventsOf(student).get(0).inAppMessages().get(0);
        assertThat(stored.getInAppMessages()).containsExactly(fromTheEvent);
        assertThat(stored.getEmailMessages()).isEmpty();
        assertThat(stored.getSuppressed()).isEmpty();
    }

    @Test
    @Tag("D-368")
    @Tag("D-413")
    void courseEnrollmentFactsCarryTheCourseIdAndProgramEnrollmentFactsTheProgramId() {
        Student student = newStudent();
        Course course = newCourse();
        CourseEnrollment courseEnrollment = enrollInCourse(student, course);
        ProgramEnrollment programEnrollment = enrollInProgram(student);
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, programEnrollment.getId(), EnrollmentPaymentStatus.SUSPENDED);

        EnrollmentReminderTarget courseTarget = targetLookup.findCourseEnrollmentTargets(List.of(courseEnrollment.getId())).get(courseEnrollment.getId());
        EnrollmentReminderTarget programTarget = targetLookup.findProgramEnrollmentTargets(List.of(programEnrollment.getId())).get(programEnrollment.getId());

        assertThat(courseTarget).isEqualTo(new EnrollmentReminderTarget(courseEnrollment.getId(), null, course.getId(), false));
        assertThat(programTarget).isEqualTo(new EnrollmentReminderTarget(programEnrollment.getId(), programEnrollment.getProgram().getId(), null, true));
        assertThat(targetLookup.findCourseEnrollmentTargets(List.of())).isEmpty();
    }
}
