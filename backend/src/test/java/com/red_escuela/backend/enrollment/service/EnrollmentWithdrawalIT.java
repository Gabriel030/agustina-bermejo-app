package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import java.time.Duration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("D-142")
@Tag("D-296")
@Tag("D-236")
class EnrollmentWithdrawalIT extends AbstractCreditNoteIT {

    private static final LocalDateTime ENROLLED_AT = LocalDateTime.of(2027, 3, 1, 10, 0);

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    private record StudentWithAccount(Long studentId, Long accountId) {
    }

    private StudentWithAccount newStudentWithAccount() {
        Long studentId = newStudentId();
        return new StudentWithAccount(studentId, newAccountId(studentId, Currency.ARS));
    }

    private Long newProgramEnrollment(Long studentId, LocalDateTime enrolledAt) {
        Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class,
                "Withdrawal " + System.nanoTime());
        return inTransaction(() -> {
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
            enrollment.setProgram(entityManager.getReference(Program.class, programId));
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(enrolledAt);
            return programEnrollmentRepository.save(enrollment).getId();
        });
    }

    private Long newCourseEnrollment(Long studentId, EnrollmentStatus status) {
        return inTransaction(() -> {
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setTerm(term);
            course.setName("Curso de baja " + System.nanoTime());
            course.setIsActive(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            entityManager.persist(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
            enrollment.setCourse(course);
            enrollment.setStatus(status);
            enrollment.setEnrollmentDate(ENROLLED_AT);
            return courseEnrollmentRepository.save(enrollment).getId();
        });
    }

    private Long issuePaidAnnualInvoice(StudentWithAccount student, Long programEnrollmentId) {
        Long invoiceId = inTransaction(() -> {
            Person person = studentRepository.findById(student.studentId()).orElseThrow().getPerson();
            Long programId = jdbc.queryForObject("select program_id from program_enrollment where id = ?", Long.class, programEnrollmentId);
            Long revisionId = PriceFixtures.persistPriceVersion(programId, null, FeeType.MONTHLY_INSTALLMENT, "Anual", Money.of(new BigDecimal("80000.00"), Currency.ARS),
                    LocalDate.of(2027, 1, 1), person.getId());
            long tag = System.nanoTime();
            List<InvoiceLineDraft> lines = new ArrayList<>();
            for (int month = 3; month <= 12; month++) {
                lines.add(new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + month, YearMonth.of(2027, month), programEnrollmentId, null, null, null, revisionId,
                        "ANN:" + tag + ":" + month, ars("80000.00")));
            }
            InvoiceDraftHeader header = new InvoiceDraftHeader(student.accountId(), Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                    InvoiceIssuingReason.ANNUAL_UPFRONT_PAYMENT, null);
            Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines, Map.of(), ACTOR);
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), LocalDate.of(2027, 2, 15), LocalDate.of(2027, 3, 10), snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(student.accountId()), saved, LocalDate.of(2027, 2, 15));
            return saved.getId();
        });
        payAccount(student.accountId(), "800000.00", LocalDate.of(2027, 2, 20));
        assertThat(paymentStatusOf(invoiceId)).isEqualTo("PAID");
        return invoiceId;
    }

    private Invoice issueMonthlyInstallment(Long programEnrollmentId, YearMonth period, LocalDate dueDate, boolean overdue) {
        ProgramEnrollment enrollment = inTransaction(() -> programEnrollmentRepository.findById(programEnrollmentId).orElseThrow());
        BillingTestFixtures.InvoiceFixture fixture = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(period).dueOn(dueDate)
                .withBaseAmount(Money.of(new BigDecimal("80000.00"), Currency.ARS));
        return (overdue ? fixture.overdue() : fixture.pending()).build();
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private Map<String, Object> billingStateOf(Long accountId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("creditNotes", jdbc.queryForObject("select count(*) from credit_note where student_current_account_id = ?", Long.class, accountId));
        state.put("entries", jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId));
        state.put("allocations", jdbc.queryForObject("select count(*) from payment_allocation where student_current_account_id = ?", Long.class, accountId));
        state.put("statusChanges", jdbc.queryForObject(
                "select count(*) from invoice_status_change c join invoice i on i.id = c.invoice_id where i.student_current_account_id = ?", Long.class, accountId));
        state.put("account", jdbc.queryForMap("select balance_amount, unallocated_credit_amount from student_current_account where id = ?", accountId));
        state.put("invoices", jdbc.queryForList("select id, payment_status, document_status, total, outstanding_amount, due_date "
                + "from invoice where student_current_account_id = ? order by id", accountId));
        state.put("lines", jdbc.queryForList("select l.id, l.amount, l.credited_amount, l.charge_key_released_at from invoice_line l join invoice i on i.id = l.invoice_id "
                + "where i.student_current_account_id = ? order by l.id", accountId));
        return state;
    }

    private MvcResult withdrawProgram(Long enrollmentId, Staff staff, String body, int expectedStatus) throws Exception {
        return perform("/program-enrollments/" + enrollmentId + "/withdraw", staff, body, expectedStatus);
    }

    private MvcResult withdrawCourse(Long enrollmentId, Staff staff, String body, int expectedStatus) throws Exception {
        return perform("/course-enrollments/" + enrollmentId + "/withdraw", staff, body, expectedStatus);
    }

    private MvcResult perform(String path, Staff staff, String body, int expectedStatus) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + staff.token());
        if (body != null) {
            request.contentType(APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    }

    private String effectiveDateOf(Long programEnrollmentId) {
        return jdbc.queryForObject("select withdrawal_effective_date::text from program_enrollment where id = ?", String.class, programEnrollmentId);
    }

    private long authorizationRequestCount() {
        return jdbc.queryForObject("select count(*) from authorization_request", Long.class);
    }

    @Test
    @Tag("C-03")
    void withdrawingMidMonthWithThePaidYearLeavesAnnualInvoiceAndAccountUntouched() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        Long annualInvoiceId = issuePaidAnnualInvoice(student, enrollmentId);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));
        Map<String, Object> before = billingStateOf(student.accountId());
        long requestsBefore = authorizationRequestCount();

        withdrawProgram(enrollmentId, newStaff("ADMINISTRATIVO"), null, 204);

        assertThat(billingStateOf(student.accountId())).isEqualTo(before);
        assertThat(paymentStatusOf(annualInvoiceId)).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", student.accountId())).isEqualByComparingTo("0.00");
        assertThat(authorizationRequestCount()).isEqualTo(requestsBefore);
        assertThat(jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("WITHDRAWN");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-04")
    void withdrawingKeepsTheUnpaidAndOverdueInvoicesAsDebt() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        Invoice april = issueMonthlyInstallment(enrollmentId, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 10), true);
        Invoice may = issueMonthlyInstallment(enrollmentId, YearMonth.of(2027, 5), LocalDate.of(2027, 5, 10), false);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));
        Map<String, Object> before = billingStateOf(student.accountId());

        withdrawProgram(enrollmentId, newStaff("ADMINISTRATIVO"), null, 204);

        assertThat(billingStateOf(student.accountId())).isEqualTo(before);
        assertThat(paymentStatusOf(april.getId())).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(may.getId())).isEqualTo("PENDING");
        invariants.assertAllBillingInvariantsHold();
    }

    @ParameterizedTest(name = "withdrawal on {0} is dated {1} and emits no credit note")
    @Tag("C-01")
    @Tag("C-23")
    @CsvSource({
            "2027-05-01T00:00:00, 2027-05-01",
            "2027-05-15T12:00:00, 2027-05-15",
            "2027-05-31T23:59:00, 2027-05-31",
            "2027-06-01T00:30:00, 2027-06-01",
            "2027-12-31T23:59:00, 2027-12-31",
            "2028-01-01T00:30:00, 2028-01-01"
    })
    void withdrawalOnTheBorderOfAMonthIsDatedWithTheLocalDateAndEmitsNoCreditNote(String clockMoment, String expectedDate) throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        issuePaidAnnualInvoice(student, enrollmentId);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.parse(clockMoment));
        Map<String, Object> before = billingStateOf(student.accountId());

        withdrawProgram(enrollmentId, newStaff("DIRECTOR"), null, 204);

        assertThat(effectiveDateOf(enrollmentId)).isEqualTo(expectedDate);
        assertThat(jdbc.queryForObject("select completion_date::date::text from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo(expectedDate);
        assertThat(billingStateOf(student.accountId())).isEqualTo(before);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-24")
    void informedDateOfTheWarningMonthIsAuditedWithTheUserWhoRegisteredIt() throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 3));
        Staff administrative = newStaff("ADMINISTRATIVO");

        withdrawProgram(enrollmentId, administrative, "{\"effectiveDate\":\"2027-04-28\"}", 204);

        assertThat(effectiveDateOf(enrollmentId)).isEqualTo("2027-04-28");
        assertThat(jdbc.queryForObject("select withdrawn_by_person_id from program_enrollment where id = ?", Long.class, enrollmentId)).isEqualTo(administrative.personId());
        assertThat(jdbc.queryForObject("select completion_date::date::text from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("2027-04-28");
    }

    @ParameterizedTest(name = "today {0}, enrolled {1}, informed {2} answers {3}")
    @Tag("C-24")
    @CsvSource({
            "2027-05-15, 2027-03-01T10:00:00, 2027-05-15, 204",
            "2027-05-15, 2027-03-01T10:00:00, 2027-04-01, 204",
            "2027-05-15, 2027-03-01T10:00:00, 2027-03-31, 400",
            "2027-05-15, 2027-03-01T10:00:00, 2027-05-16, 400",
            "2028-01-15, 2027-03-01T10:00:00, 2027-12-01, 204",
            "2028-01-15, 2027-03-01T10:00:00, 2027-11-30, 400",
            "2027-05-15, 2027-04-10T10:00:00, 2027-04-09, 400",
            "2027-05-15, 2027-04-10T10:00:00, 2027-04-10, 204"
    })
    void informedDateOutsideTheAllowedRangeIsRejectedWithoutChangingTheEnrollment(String today, String enrolledAt, String informed, int expectedStatus) throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), LocalDateTime.parse(enrolledAt));
        MutableClock.INSTANCE.setDate(LocalDate.parse(today));
        Map<String, Object> before = billingStateOf(student.accountId());

        MvcResult result = withdrawProgram(enrollmentId, newStaff("ADMINISTRATIVO"), "{\"effectiveDate\":\"" + informed + "\"}", expectedStatus);

        if (expectedStatus == 400) {
            assertThat(result.getResponse().getContentAsString()).contains("WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE");
            assertThat(jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("ENROLLED");
            assertThat(effectiveDateOf(enrollmentId)).isNull();
            assertThat(jdbc.queryForObject("select completion_date from program_enrollment where id = ?", java.sql.Timestamp.class, enrollmentId)).isNull();
        } else {
            assertThat(effectiveDateOf(enrollmentId)).isEqualTo(informed);
        }
        assertThat(billingStateOf(student.accountId())).isEqualTo(before);
    }

    @Test
    @Tag("C-31")
    @Tag("D-250")
    void onlyAdministrativeAndDirectorCanRegisterAProgramWithdrawal() throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));

        withdrawProgram(enrollmentId, newStaff("PRECEPTOR"), null, 403);
        withdrawProgram(enrollmentId, newStaff("ALUMNO"), null, 403);
        assertThat(jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("ENROLLED");

        withdrawProgram(enrollmentId, newStaff("DIRECTOR"), null, 204);

        Long otherEnrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        withdrawProgram(otherEnrollmentId, newStaff("ADMINISTRATIVO"), null, 204);
    }

    @Test
    @Tag("C-31")
    @Tag("D-250")
    void onlyAdministrativeAndDirectorCanRegisterACourseWithdrawal() throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newCourseEnrollment(student.studentId(), EnrollmentStatus.WAITLISTED);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));

        withdrawCourse(enrollmentId, newStaff("PRECEPTOR"), null, 403);
        withdrawCourse(enrollmentId, newStaff("ALUMNO"), null, 403);
        assertThat(jdbc.queryForObject("select status from course_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("WAITLISTED");

        withdrawCourse(enrollmentId, newStaff("DIRECTOR"), "{\"effectiveDate\":\"2027-05-10\"}", 204);

        assertThat(jdbc.queryForObject("select withdrawal_effective_date::text from course_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("2027-05-10");
        Long otherEnrollmentId = newCourseEnrollment(student.studentId(), EnrollmentStatus.WAITLISTED);
        withdrawCourse(otherEnrollmentId, newStaff("ADMINISTRATIVO"), null, 204);
    }

    @Test
    @Tag("C-22")
    void twoSimultaneousWithdrawalsOfTheSameEnrollmentLeaveOneWithdrawalAndAConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        StudentWithAccount student = newStudentWithAccount();
        Long enrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        issuePaidAnnualInvoice(student, enrollmentId);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));
        Staff administrative = newStaff("ADMINISTRATIVO");
        Map<String, Object> before = billingStateOf(student.accountId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> answers = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            answers.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/program-enrollments/" + enrollmentId + "/withdraw").header("Authorization", "Bearer " + administrative.token()))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> answer : answers) {
            statuses.add(answer.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(jdbc.queryForObject("select count(*) from program_enrollment where id = ? and status = 'WITHDRAWN'", Long.class, enrollmentId)).isEqualTo(1L);
        assertThat(billingStateOf(student.accountId())).isEqualTo(before);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-25")
    void studentFileWithdrawalIsRejectedWhileAnyEnrollmentIsActive() throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long programEnrollmentId = newProgramEnrollment(student.studentId(), ENROLLED_AT);
        Long waitlistedCourseId = newCourseEnrollment(student.studentId(), EnrollmentStatus.WAITLISTED);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 15));
        Staff administrative = newStaff("ADMINISTRATIVO");
        String body = "{\"reason\":\"ABANDONO\",\"detail\":\"Se mudó\"}";

        mockMvc.perform(post("/students/" + student.studentId() + "/withdraw").header("Authorization", "Bearer " + administrative.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_HAS_ACTIVE_ENROLLMENTS"));
        assertThat(studentPersonStatus(student.studentId())).isEqualTo("ACTIVE");

        withdrawProgram(programEnrollmentId, administrative, null, 204);
        mockMvc.perform(post("/students/" + student.studentId() + "/withdraw").header("Authorization", "Bearer " + administrative.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_HAS_ACTIVE_ENROLLMENTS"));
        assertThat(studentPersonStatus(student.studentId())).isEqualTo("ACTIVE");

        withdrawCourse(waitlistedCourseId, administrative, null, 204);
        mockMvc.perform(post("/students/" + student.studentId() + "/withdraw").header("Authorization", "Bearer " + administrative.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(studentPersonStatus(student.studentId())).isEqualTo("DELETED");
    }

    @Test
    @Tag("C-25")
    @Tag("D-250")
    void onlyAdministrativeAndDirectorCanWithdrawAStudentFile() throws Exception {
        String body = "{\"reason\":\"ABANDONO\",\"detail\":\"Se mudó\"}";
        Long studentForPreceptor = newStudentId();
        Long studentForAdministrative = newStudentId();
        Long studentForDirector = newStudentId();

        mockMvc.perform(post("/students/" + studentForPreceptor + "/withdraw").header("Authorization", "Bearer " + newStaff("PRECEPTOR").token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/students/" + studentForPreceptor + "/withdraw").header("Authorization", "Bearer " + newStaff("ALUMNO").token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertThat(studentPersonStatus(studentForPreceptor)).isEqualTo("ACTIVE");

        mockMvc.perform(post("/students/" + studentForAdministrative + "/withdraw").header("Authorization", "Bearer " + newStaff("ADMINISTRATIVO").token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mockMvc.perform(post("/students/" + studentForDirector + "/withdraw").header("Authorization", "Bearer " + newStaff("DIRECTOR").token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(studentPersonStatus(studentForAdministrative)).isEqualTo("DELETED");
        assertThat(studentPersonStatus(studentForDirector)).isEqualTo("DELETED");
    }

    @Test
    @Tag("C-25")
    void anEnrollmentCreatedWhileTheFileWithdrawalWaitsForTheStudentLockKeepsTheFileActive() throws Exception {
        StudentWithAccount student = newStudentWithAccount();
        Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class,
                "Race " + System.nanoTime());
        Staff administrative = newStaff("ADMINISTRATIVO");
        CountDownLatch enrollmentInserted = new CountDownLatch(1);
        CountDownLatch releaseEnrollment = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> enrolling = pool.submit(() -> transactionTemplate.execute(status -> {
            programEnrollmentService.enrollAfterAdmissionPayment(student.studentId(), new CreateProgramEnrollmentRequest(programId, null));
            enrollmentInserted.countDown();
            awaitQuietly(releaseEnrollment);
            return null;
        }));
        enrollmentInserted.await();
        Future<Integer> withdrawing = pool.submit(() -> mockMvc.perform(post("/students/" + student.studentId() + "/withdraw")
                .header("Authorization", "Bearer " + administrative.token()).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"ABANDONO\",\"detail\":\"x\"}")).andReturn().getResponse().getStatus());
        try {
            await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class) > 0);
        } finally {
            releaseEnrollment.countDown();
        }
        enrolling.get();
        int withdrawalStatus = withdrawing.get();
        pool.shutdown();

        assertThat(withdrawalStatus).isEqualTo(409);
        assertThat(studentPersonStatus(student.studentId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from program_enrollment where student_id = ? and status = 'ENROLLED'", Long.class, student.studentId())).isEqualTo(1L);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String studentPersonStatus(Long studentId) {
        return inTransaction(() -> studentRepository.findById(studentId).orElseThrow().getPerson().getStatus().name());
    }
}
