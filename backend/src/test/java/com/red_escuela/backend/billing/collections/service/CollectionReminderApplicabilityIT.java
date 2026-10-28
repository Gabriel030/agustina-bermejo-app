package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.api.CollectionReminderApplicabilityQuery;
import com.red_escuela.backend.billing.collections.domain.CollectionReminder;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRepository;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CollectionReminderApplicabilityIT extends AbstractIntegrationTest {
    private static final LocalDate MARCH_FIRST = LocalDate.of(2027, 3, 1);
    private static final LocalDate MARCH_ELEVENTH = LocalDate.of(2027, 3, 11);

    @Autowired
    private CollectionReminderApplicabilityQuery applicabilityQuery;
    @Autowired
    private CollectionReminderRepository reminderRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
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

    private Student student;
    private CourseEnrollment enrollment;

    @BeforeEach
    void setUp() {
        com.red_escuela.backend.shared.time.ApplicationTime.useClock(MutableClock.INSTANCE);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 9, 5, 0));
        Institution institution = new Institution();
        institution.setName("Instituto aplicabilidad " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Term term = new Term();
        term.setYear(2027 + (int) (System.nanoTime() % 1000));
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        Course course = new Course();
        course.setName("Curso aplicabilidad " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);
        student = new Student();
        student.setPerson(newPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);
        enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        enrollment = courseEnrollmentRepository.save(enrollment);
    }

    private Person newPerson() {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = "alumno." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Aplicabilidad");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            return personRepository.save(person);
        });
    }

    private Invoice installment() {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2027, 3)).dueOn(MARCH_ELEVENTH).withBaseAmount(ars(100000)).build();
        jdbc.update("update invoice set issue_date = ?, due_date = ? where id = ?", MARCH_FIRST, MARCH_ELEVENTH, invoice.getId());
        return invoice;
    }

    private Long storedReminderWithMailNoticesOn(Invoice... invoices) {
        PlannedReminderLine[] lines = Arrays.stream(invoices)
                .map(invoice -> line(invoice.getId(), MARCH_ELEVENTH, ars(100000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER)).toArray(PlannedReminderLine[]::new);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(lines));
        CollectionReminder reminder = reminderRepository.save(CollectionReminder.recordPlannedFor(student.getId(), LocalDate.of(2027, 3, 9), List.of(message), List.of(message), List.of()));
        return reminder.getId();
    }

    @Test
    @Tag("d1-17")
    @Tag("D-354")
    void stillApplicableIsFalseOncePaid() {
        Invoice invoice = installment();
        Long reminderId = storedReminderWithMailNoticesOn(invoice);
        boolean applicableBeforePaying = applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9));

        BillingTestFixtures.payInvoiceInFull(invoice);

        assertThat(applicableBeforePaying).isTrue();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9))).isFalse();
    }

    @Test
    @Tag("D-354")
    void aPaidInvoiceIsNotApplicableEvenWhenAnotherInvoiceOfTheSameReminderIsStillOwed() {
        Invoice paid = installment();
        Invoice owed = installment();
        Long reminderId = storedReminderWithMailNoticesOn(paid, owed);
        BillingTestFixtures.payInvoiceInFull(paid);
        LocalDate day = LocalDate.of(2027, 3, 9);

        assertThat(applicabilityQuery.isStillApplicable(reminderId, day)).isTrue();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, day, java.util.Set.of(paid.getId()))).isFalse();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, day, java.util.Set.of(owed.getId()))).isTrue();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, day, java.util.Set.of(paid.getId(), owed.getId()))).isTrue();
    }

    @Test
    @Tag("D-354")
    void anOverdueInvoiceKeepsTheReminderApplicable() {
        Invoice invoice = installment();
        Long reminderId = storedReminderWithMailNoticesOn(invoice);

        BillingTestFixtures.markInvoiceAsOverdue(invoice);

        assertThat(applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 12))).isTrue();
    }

    @Test
    @Tag("D-346")
    void aReceiptUnderReviewMakesTheReminderNotApplicableUntilItIsNoLongerUnderReview() {
        Invoice invoice = installment();
        Long reminderId = storedReminderWithMailNoticesOn(invoice);

        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'comprobante.pdf', now(), 'PENDING_REVIEW', 0)", invoice.getId());
        boolean whileUnderReview = applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9));
        jdbc.update("delete from bank_transfer_receipt where invoice_id = ?", invoice.getId());

        assertThat(whileUnderReview).isFalse();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9))).isTrue();
    }

    @Test
    @Tag("D-247")
    void anOpenCancellationRequestMakesTheReminderNotApplicable() {
        Invoice invoice = installment();
        Long reminderId = storedReminderWithMailNoticesOn(invoice);
        Long requestId = jdbc.queryForObject("insert into authorization_request (operation_type, operation_payload, required_role, reason, status, requested_by_id, requested_at) "
                + "values ('CANCEL_INVOICE', '{}'::jsonb, 'DIRECTOR', 'Baja', 'PENDING', ?, now()) returning id", Long.class, student.getPerson().getId());
        jdbc.update("insert into authorization_request_invoice (authorization_request_id, invoice_id) values (?, ?)", requestId, invoice.getId());

        assertThat(applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9))).isFalse();
    }

    @Test
    @Tag("D-354")
    void oneInvoiceStillOpenKeepsTheReminderApplicableAndPayingTheLastOneEndsIt() {
        Invoice first = installment();
        Invoice second = installment();
        Long reminderId = storedReminderWithMailNoticesOn(first, second);

        BillingTestFixtures.payInvoiceInFull(first);
        boolean withTheSecondOpen = applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9));
        BillingTestFixtures.payInvoiceInFull(second);

        assertThat(withTheSecondOpen).isTrue();
        assertThat(applicabilityQuery.isStillApplicable(reminderId, LocalDate.of(2027, 3, 9))).isFalse();
    }

    @Test
    @Tag("D-354")
    void anUnknownReminderOrOneWithoutMailsIsNotApplicable() {
        Invoice invoice = installment();
        PlannedReminderMessage inAppOnly = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true,
                arsBlock(line(invoice.getId(), MARCH_ELEVENTH, ars(100000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER)));
        Long inAppOnlyReminderId = reminderRepository.save(CollectionReminder.recordPlannedFor(student.getId(), LocalDate.of(2027, 3, 9), List.of(inAppOnly), List.of(), List.of())).getId();

        assertThat(applicabilityQuery.isStillApplicable(Long.MAX_VALUE, LocalDate.of(2027, 3, 9))).isFalse();
        assertThat(applicabilityQuery.isStillApplicable(inAppOnlyReminderId, LocalDate.of(2027, 3, 9))).isFalse();
    }
}
