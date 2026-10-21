package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
@SlowTest(reason = "Thirteen scenarios that each seed enrollments and invoices, withdraw with a past end date and wait for the asynchronous correction events against Postgres")
class LateFeeCorrectionAfterWithdrawalIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired
    private com.red_escuela.backend.academicstructure.repository.ProgramRepository programRepository;
    @Autowired
    private com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private com.red_escuela.backend.enrollment.service.ProgramEnrollmentService programEnrollmentService;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private void withdrawRegisteredOn(LocalDate registeredOn, LocalDate informed) {
        MutableClock.INSTANCE.setDate(registeredOn);
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(informed, person.getId()));
        awaitEventsDrained();
    }

    private Long debitNoteIdOf(Invoice origin, int index) {
        return (Long) chargesOf(origin).get(index).get("debit_note_id");
    }

    private List<Map<String, Object>> creditNotesOf(Long invoiceId) {
        return jdbc.queryForList("select * from credit_note where invoice_id = ? order by id", invoiceId);
    }

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private BigDecimal unallocatedCreditOf(Invoice origin) {
        return decimal("select unallocated_credit_amount from student_current_account where id = ?", origin.getStudentCurrentAccountId());
    }

    private void payDebitNote(Invoice origin, Long debitNoteId, String amount, LocalDate day) {
        receiveMoney(origin, amount, day);
        transactionTemplate.executeWithoutResult(status -> allocationService.allocateAccountCreditToOldestInvoicesSkipping(
                accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()), day, ACTOR,
                java.util.Set.of(origin.getId())));
        assertThat(text("select payment_status from invoice where id = ?", debitNoteId)).isEqualTo("PAID");
    }

    @Test
    @Tag("D-264")
    void aPaidDebitNoteReceivesTheCorrectionAsFreeCreditThatStaysInFavorWhenNothingCanBeCovered() {
        arrange(2109);
        publishDefaults(2109);
        Invoice origin = overdueInstallmentDueOn(d(2109, 4, 11));
        reconcile(origin, d(2109, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2109, 5, 2));
        payDebitNote(origin, debitNoteId, "3800.00", d(2109, 5, 2));

        withdrawRegisteredOn(d(2109, 5, 3), d(2109, 4, 28));

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("0.00");
        assertThat(text("select payment_status from invoice where id = ?", debitNoteId)).isEqualTo("PAID");
        assertThat(decimal("select credited_amount from invoice where id = ?", debitNoteId)).isEqualByComparingTo("0.00");
        assertThat(unallocatedCreditOf(origin)).isEqualByComparingTo("400.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    @Tag("D-136")
    void theFreeCreditOfAPaidDebitNoteIsAppliedInTheActToTheOldestDebtItCanCoverWithoutPartialPayments() {
        arrange(2110);
        publishDefaults(2110);
        Invoice origin = overdueInstallmentDueOn(d(2110, 4, 11));
        reconcile(origin, d(2110, 5, 1));
        MutableClock.INSTANCE.setDate(d(2110, 5, 2));
        payInFull(origin, d(2110, 5, 2), d(2110, 5, 2));
        receiveMoney(origin, "3800.00", d(2110, 5, 2));
        allocateOn(origin, d(2110, 5, 2));
        awaitEventsDrained();
        assertThat(summaryOf(origin)).containsExactly("2110-04-12..2110-04-30:19:3800.00", "2110-05-01..2110-05-02:2:400.00");
        Long aprilNote = debitNoteIdOf(origin, 0);
        Long settlementNote = debitNoteIdOf(origin, 1);
        assertThat(text("select payment_status from invoice where id = ?", aprilNote)).isEqualTo("PAID");
        assertThat(text("select payment_status from invoice where id = ?", settlementNote)).isEqualTo("PENDING");

        withdrawRegisteredOn(d(2110, 5, 3), d(2110, 4, 28));

        assertThat(creditNotesOf(aprilNote)).hasSize(1);
        assertThat((BigDecimal) creditNotesOf(aprilNote).get(0).get("free_credit_amount")).isEqualByComparingTo("400.00");
        assertThat(text("select payment_status from invoice where id = ?", settlementNote)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ? and reverses_allocation_id is null", Long.class, settlementNote)).isEqualTo(1L);
        assertThat(unallocatedCreditOf(origin)).isEqualByComparingTo("400.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-127")
    @Tag("D-202")
    @Tag("D-249")
    void aBackdatedWithdrawalCancelsTheDebitNotesWhoseDaysAreAllAfterTheEndWithoutReleasingTheirKeyAndCreditsTheRestPerDebitNote() {
        arrange(2111);
        publishDefaults(2111);
        Invoice marchOrigin = overdueInstallmentDueOn(d(2111, 3, 11));
        Invoice aprilOrigin = overdueInstallmentDueOn(d(2111, 4, 11));
        reconcile(marchOrigin, d(2111, 5, 1));
        reconcile(aprilOrigin, d(2111, 5, 1));
        assertThat(summaryOf(marchOrigin)).containsExactly("2111-03-12..2111-03-31:20:4000.00", "2111-04-01..2111-04-30:30:6000.00");
        assertThat(summaryOf(aprilOrigin)).containsExactly("2111-04-12..2111-04-30:19:3800.00");
        Long marchOfMarch = debitNoteIdOf(marchOrigin, 0);
        Long aprilOfMarch = debitNoteIdOf(marchOrigin, 1);
        Long aprilOfApril = debitNoteIdOf(aprilOrigin, 0);

        withdrawRegisteredOn(d(2111, 5, 31), d(2111, 4, 1));

        assertThat(creditNotesOf(marchOfMarch)).isEmpty();
        assertThat(text("select payment_status from invoice where id = ?", marchOfMarch)).isNotEqualTo("CANCELLED");
        List<Map<String, Object>> reduction = creditNotesOf(aprilOfMarch);
        assertThat(reduction).hasSize(1);
        assertThat((BigDecimal) reduction.get(0).get("total_amount")).isEqualByComparingTo("5800.00");
        assertThat(decimal("select outstanding_amount from invoice where id = ?", aprilOfMarch)).isEqualByComparingTo("200.00");
        assertThat(text("select payment_status from invoice where id = ?", aprilOfMarch)).isNotEqualTo("CANCELLED");
        List<Map<String, Object>> cancellation = creditNotesOf(aprilOfApril);
        assertThat(cancellation).hasSize(1);
        assertThat((BigDecimal) cancellation.get(0).get("total_amount")).isEqualByComparingTo("3800.00");
        assertThat(cancellation.get(0)).containsEntry("reason", "LATE_FEE_CORRECTION");
        assertThat(text("select payment_status from invoice where id = ?", aprilOfApril)).isEqualTo("CANCELLED");
        assertThat(text("select count(charge_key_released_at)::text from invoice_line where invoice_id = ?", aprilOfApril)).isEqualTo("0");
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id in (?, ?, ?)", Long.class, marchOfMarch, aprilOfMarch, aprilOfApril))
                .isEqualTo(2L);
        assertThat(jdbc.queryForList("select first_corrected_day::text f, last_corrected_day::text l, corrected_days d from late_fee_charge_correction where debit_note_id = ?", aprilOfMarch))
                .containsExactly(Map.of("f", "2111-04-02", "l", "2111-04-30", "d", 29));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-127")
    void aPaidDebitNoteWhoseDaysAreAllAfterTheEndKeepsPaidAndReceivesTheWholeAmountAsFreeCredit() {
        arrange(2112);
        publishDefaults(2112);
        Invoice origin = overdueInstallmentDueOn(d(2112, 4, 11));
        reconcile(origin, d(2112, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2112, 5, 2));
        payDebitNote(origin, debitNoteId, "3800.00", d(2112, 5, 2));

        withdrawRegisteredOn(d(2112, 5, 31), d(2112, 4, 1));

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("3800.00");
        assertThat(text("select payment_status from invoice where id = ?", debitNoteId)).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(origin)).isEqualByComparingTo("3800.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-332")
    void theFinalDebitNoteOfAPaymentIsCorrectedLikeAnyOther() {
        arrange(2113);
        publishDefaults(2113);
        Invoice origin = overdueInstallmentDueOn(d(2113, 4, 11));
        reconcile(origin, d(2113, 5, 1));
        MutableClock.INSTANCE.setDate(d(2113, 5, 2));
        payInFull(origin, d(2113, 5, 2), d(2113, 5, 2));
        awaitEventsDrained();
        assertThat(summaryOf(origin)).containsExactly("2113-04-12..2113-04-30:19:3800.00", "2113-05-01..2113-05-02:2:400.00");
        Long aprilNote = debitNoteIdOf(origin, 0);
        Long settlementNote = debitNoteIdOf(origin, 1);

        withdrawRegisteredOn(d(2113, 5, 3), d(2113, 4, 28));

        assertThat((BigDecimal) creditNotesOf(aprilNote).get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) creditNotesOf(settlementNote).get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(text("select payment_status from invoice where id = ?", settlementNote)).isEqualTo("CANCELLED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-249")
    void aWithdrawalInformedForTodayLeavesEveryIssuedDebitNoteUntouched() {
        arrange(2114);
        publishDefaults(2114);
        Invoice origin = overdueInstallmentDueOn(d(2114, 4, 11));
        reconcile(origin, d(2114, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);

        withdrawRegisteredOn(d(2114, 5, 3), null);

        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-148")
    @Tag("D-360")
    void whenTheVersionInEffectOnTheEndDateDoesNotFreezeNothingIsCorrectedEvenIfALaterVersionFreezes() {
        arrange(2115);
        publish(0, "0.002", 10, false, true, d(2115, 1, 1));
        publish(0, "0.002", 10, true, true, d(2115, 5, 1));
        Invoice origin = overdueInstallmentDueOn(d(2115, 4, 11));
        reconcile(origin, d(2115, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);

        withdrawRegisteredOn(d(2115, 5, 3), d(2115, 4, 28));

        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(summaryOf(origin)).containsExactly("2115-04-12..2115-04-30:19:3800.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-293")
    void onlyTheDebitNotesOfTheWithdrawnEnrollmentAreCorrected() {
        arrange(2116);
        publishDefaults(2116);
        Invoice withdrawnOrigin = overdueInstallmentDueOn(d(2116, 4, 11));
        Course otherCourse = new Course();
        otherCourse.setName("Otro curso " + System.nanoTime());
        otherCourse.setTerm(term);
        otherCourse.setIsActive(StatusType.ACTIVE);
        otherCourse = courseRepository.save(otherCourse);
        CourseEnrollment other = new CourseEnrollment();
        other.setStudent(student);
        other.setCourse(otherCourse);
        other.setStatus(EnrollmentStatus.ENROLLED);
        other.setEnrollmentDate(LocalDateTime.now());
        other = courseEnrollmentRepository.save(other);
        Invoice keptOrigin = BillingTestFixtures.invoice().forCourseEnrollment(other).forPeriod(YearMonth.of(2116, 4)).dueOn(d(2116, 4, 11)).withBaseAmount(ars(100000)).overdue().build();
        reconcile(withdrawnOrigin, d(2116, 5, 1));
        reconcile(keptOrigin, d(2116, 5, 1));

        withdrawRegisteredOn(d(2116, 5, 3), d(2116, 4, 28));

        assertThat(creditNotesOf(debitNoteIdOf(withdrawnOrigin, 0))).hasSize(1);
        assertThat(creditNotesOf(debitNoteIdOf(keptOrigin, 0))).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-293")
    void aProgramWithdrawalCorrectsOnlyTheDebitNotesOfThatProgramAndNotThoseOfTheCourse() {
        arrange(2122);
        publishDefaults(2122);
        com.red_escuela.backend.academicstructure.entity.Program program = new com.red_escuela.backend.academicstructure.entity.Program();
        program.setName("Carrera recargo " + System.nanoTime());
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        program = programRepository.save(program);
        com.red_escuela.backend.enrollment.entity.ProgramEnrollment programEnrollment = new com.red_escuela.backend.enrollment.entity.ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        programEnrollment = programEnrollmentRepository.save(programEnrollment);
        Invoice programOrigin = BillingTestFixtures.invoice().forProgramEnrollment(programEnrollment).forPeriod(YearMonth.of(2122, 4)).dueOn(d(2122, 4, 11))
                .withBaseAmount(ars(100000)).overdue().build();
        Invoice courseOrigin = overdueInstallmentDueOn(d(2122, 4, 11));
        reconcile(programOrigin, d(2122, 5, 1));
        reconcile(courseOrigin, d(2122, 5, 1));

        MutableClock.INSTANCE.setDate(d(2122, 5, 3));
        programEnrollmentService.withdraw(programEnrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2122, 4, 28), person.getId()));
        awaitEventsDrained();

        assertThat((BigDecimal) creditNotesOf(debitNoteIdOf(programOrigin, 0)).get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(creditNotesOf(debitNoteIdOf(courseOrigin, 0))).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-293")
    void theDebitNoteOfAnEnrollmentFeeIsCorrectedLikeThatOfAnInstallment() {
        arrange(2117);
        publishDefaults(2117);
        Invoice enrollmentFee = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.of(2117, 4)).dueOn(d(2117, 4, 11))
                .withBaseAmount(ars(100000)).overdue().build();
        reconcile(enrollmentFee, d(2117, 5, 1));
        Long debitNoteId = debitNoteIdOf(enrollmentFee, 0);

        withdrawRegisteredOn(d(2117, 5, 3), d(2117, 4, 28));

        assertThat((BigDecimal) creditNotesOf(debitNoteId).get(0).get("total_amount")).isEqualByComparingTo("400.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-216")
    @Tag("D-250")
    void theCorrectionIsAnAuditedSystemOperationWithoutAnyAuthorizationRequestOrPerson() {
        arrange(2119);
        publishDefaults(2119);
        Invoice origin = overdueInstallmentDueOn(d(2119, 4, 11));
        reconcile(origin, d(2119, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);

        withdrawRegisteredOn(d(2119, 5, 3), d(2119, 4, 28));

        Map<String, Object> creditNote = creditNotesOf(debitNoteId).get(0);
        assertThat(creditNote.get("authorization_request_id")).isNull();
        assertThat(creditNote.get("issued_by_person_id")).isNull();
        assertThat(creditNote).containsEntry("issued_by_process", "LateFeeWithdrawalCorrection");
        assertThat(creditNote.get("justification").toString()).contains("2 días posteriores a la baja informada del 28/04/2119");
        assertThat(jdbc.queryForObject("select withdrawn_by_person_id from course_enrollment where id = ?", Long.class, enrollment.getId())).isEqualTo(person.getId());
    }

    @Test
    @Tag("D-244")
    void aRegistrationInTheLocalEveningThatIsAlreadyTomorrowInUtcDatesTheCreditNoteWithTheLocalDay() {
        arrange(2120);
        publishDefaults(2120);
        Invoice origin = overdueInstallmentDueOn(d(2120, 4, 11));
        reconcile(origin, d(2120, 5, 1));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setInstant(Instant.parse("2120-05-03T01:30:00Z"));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2120, 4, 28), person.getId()));
        awaitEventsDrained();

        Map<String, Object> creditNote = creditNotesOf(debitNoteId).get(0);
        assertThat(creditNote.get("fact_date").toString()).isEqualTo("2120-05-02");
        assertThat(creditNote.get("issue_date").toString()).isEqualTo("2120-05-02");
        assertThat(text("select withdrawal_registered_on::text from course_enrollment where id = ?", enrollment.getId())).isEqualTo("2120-05-02");
    }

    @Test
    void aDebitNoteWithTheHighestAmountsKeepsTheComplementWithoutOverflowingAnyColumn() {
        arrange(2121);
        publish(0, "0.03", 10, true, true, d(2121, 1, 1));
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2121, 2)).dueOn(d(2121, 2, 11))
                .withBaseAmount(ars("2400000000.00")).overdue().build();
        reconcile(origin, d(2121, 4, 1));
        assertThat(chargesOf(origin).stream().map(c -> c.get("charged_days")).toList()).containsExactly(17, 31);
        Long marchNote = debitNoteIdOf(origin, 1);
        BigDecimal base = new BigDecimal("2400000000.00");
        BigDecimal daily = new BigDecimal("0.03");
        BigDecimal expected = base.multiply(daily).multiply(BigDecimal.valueOf(31)).setScale(2, RoundingMode.HALF_UP)
                .subtract(base.multiply(daily).multiply(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP));

        withdrawRegisteredOn(d(2121, 4, 10), d(2121, 3, 1));

        assertThat(decimal("select total from invoice where id = ?", marchNote)).isEqualByComparingTo("2232000000.00");
        List<Map<String, Object>> creditNotes = creditNotesOf(marchNote);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("select corrected_days from late_fee_charge_correction where debit_note_id = ?", Integer.class, marchNote)).isEqualTo(30);
        assertThat(creditNotesOf(debitNoteIdOf(origin, 0))).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }
}
