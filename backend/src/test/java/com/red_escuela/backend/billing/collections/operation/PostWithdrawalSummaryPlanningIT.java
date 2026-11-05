package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.billing.collections.repository.PostWithdrawalSummaryStore;
import com.red_escuela.backend.billing.collections.service.AbstractOverdueContactIT;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.red_escuela.backend.billing.run.operation.CollectionReminderPlanningBillingRunStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-338")
class PostWithdrawalSummaryPlanningIT extends AbstractOverdueContactIT {

    private static final int FIRST_DAYS_OF_MONTH_SCANNED = 4;
    private static final LocalDate JULY_TENTH = LocalDate.of(2027, 7, 10);
    private static final LocalDate JULY_THIRTY_FIRST = LocalDate.of(2027, 7, 31);

    @Autowired
    private PostWithdrawalSummaryPlanningStep planningStep;
    @Autowired
    private CollectionReminderPlanningBillingRunStep planningJob;
    @Autowired
    private PostWithdrawalSummaryStore summaryStore;

    private void runJobOn(LocalDate day) {
        MutableClock.INSTANCE.setDate(day);
        planningJob.runStepForBusinessDate(day);
    }

    private List<LocalDate> summaryMonthsOf(Student student) {
        return jdbc.queryForList("select distinct summary_month from post_withdrawal_summary where student_id = ? order by summary_month", LocalDate.class, student.getId());
    }

    private List<LocalDate> summaryBusinessDatesOf(Student student) {
        return jdbc.queryForList("select distinct business_date from post_withdrawal_summary where student_id = ? order by business_date", LocalDate.class, student.getId());
    }

    private void withdrawVoluntarily(CourseEnrollment enrollment, LocalDate effectiveDate) {
        Person registrar = newPerson("secretaria", "ADMINISTRATIVO");
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', withdrawal_effective_date = ?, withdrawal_registered_on = ?, withdrawn_by_person_id = ? where id = ?",
                effectiveDate, effectiveDate, registrar.getId(), enrollment.getId());
    }

    private int summariesOf(Student student) {
        return jdbc.queryForObject("select count(*) from post_withdrawal_summary where student_id = ?", Integer.class, student.getId());
    }

    private List<LocalDate> summaryDaysOf(Student student, LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.getDayOfMonth() > FIRST_DAYS_OF_MONTH_SCANNED) {
                continue;
            }
            int before = summariesOf(student);
            planningStep.planPostWithdrawalSummariesOf(day);
            if (summariesOf(student) > before) {
                days.add(day);
            }
        }
        return days;
    }

    @Test
    @Tag("D-338")
    void aWithdrawalOnJulyThirtyFirstWithOverdueDebtGetsASummaryOnTheFirstBusinessDayOfTheSixFollowingMonthsAndNoneAfter() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        List<LocalDate> days = summaryDaysOf(student, JULY_THIRTY_FIRST, LocalDate.of(2028, 2, 10));

        assertThat(days).containsExactly(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 9, 1), LocalDate.of(2027, 10, 1), LocalDate.of(2027, 11, 1),
                LocalDate.of(2027, 12, 1), LocalDate.of(2028, 1, 3));
    }

    @Test
    @Tag("D-338")
    void whenTheStudentPaysEverythingOnSeptemberFifteenthTheSummariesStopFromOctober() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        Invoice invoice = installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);
        List<LocalDate> beforePaying = summaryDaysOf(student, JULY_THIRTY_FIRST, LocalDate.of(2027, 9, 14));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(invoice, LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 15));

        List<LocalDate> afterPaying = summaryDaysOf(student, LocalDate.of(2027, 9, 15), LocalDate.of(2028, 2, 10));

        assertThat(beforePaying).containsExactly(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 9, 1));
        assertThat(afterPaying).isEmpty();
    }

    @Test
    @Tag("D-338")
    void aSummaryIsNotRepeatedWhenThePlanningOfTheSameDayRunsTwice() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));
        int recordedAgain = planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(recordedAgain).isZero();
        assertThat(summariesOf(student)).isEqualTo(1);
    }

    @Test
    @Tag("D-338")
    void aStudentWithTwoWithdrawnEnrollmentsGetsOneSummaryRecordPerEnrollmentOnTheSameDay() {
        Student student = newStudent();
        CourseEnrollment first = newCourseEnrollment(student);
        CourseEnrollment second = newCourseEnrollment(student);
        installmentDueOn(first, JULY_TENTH);
        installmentDueOn(second, JULY_TENTH);
        withdrawVoluntarily(first, JULY_THIRTY_FIRST);
        withdrawVoluntarily(second, LocalDate.of(2027, 7, 20));

        int students = planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(students).isGreaterThanOrEqualTo(1);
        assertThat(summariesOf(student)).isEqualTo(2);
    }

    @Test
    @Tag("D-338")
    void theDebtLinesOfAWithdrawnEnrollmentCarryTheNameOfItsCourse() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        List<WithdrawnEnrollmentDebtLine> lines = summaryStore.findPendingDebtLinesOfWithdrawnEnrollments(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 2), LocalDate.of(2027, 1, 1)).stream()
                .filter(line -> line.studentId().equals(student.getId())).toList();

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.enrollmentName()).isEqualTo(enrollment.getCourse().getName()).startsWith("Curso para contactar");
            assertThat(line.withdrawalEffectiveDate()).isEqualTo(JULY_THIRTY_FIRST);
        });
    }

    @Test
    @Tag("D-338")
    void anEnrollmentThatIsStillActiveGetsNoSummaryEvenWithOverdueDebt() {
        Student student = newStudent();
        installmentDueOn(newCourseEnrollment(student), JULY_TENTH);

        planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void aWithdrawalWithoutDebtGetsNoSummary() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void aWithdrawalThatIsNotVoluntaryIsLeftToTheNonpaymentHook() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', withdrawal_effective_date = ?, withdrawal_registered_on = ? where id = ?", JULY_THIRTY_FIRST,
                JULY_THIRTY_FIRST, enrollment.getId());

        planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-346")
    void anInvoiceWithAReceiptUnderReviewIsNotSummarized() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        putReceiptUnderReview(installmentDueOn(enrollment, JULY_TENTH));
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 2));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void nothingIsPlannedBeforeTheSummaryDayOfTheMonthNorOnAWeekend() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        assertThat(planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 1))).isZero();
        assertThat(planningStep.planPostWithdrawalSummariesOf(LocalDate.of(2027, 8, 7))).isZero();
        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void whenTheJobWasDownOnTheSummaryDayTheNextBusinessDayPlansTheSummaryOnceAndTheFollowingDaysDoNotRepeatIt() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        runJobOn(LocalDate.of(2027, 8, 3));
        runJobOn(LocalDate.of(2027, 8, 3));
        runJobOn(LocalDate.of(2027, 8, 4));
        runJobOn(LocalDate.of(2027, 8, 31));

        assertThat(summariesOf(student)).isEqualTo(1);
        assertThat(summaryBusinessDatesOf(student)).containsExactly(LocalDate.of(2027, 8, 3));
        assertThat(summaryMonthsOf(student)).containsExactly(LocalDate.of(2027, 8, 1));
    }

    @Test
    @Tag("D-338")
    void theMonthAfterARecoveredSummaryIsPlannedOnItsOwnSummaryDay() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        runJobOn(LocalDate.of(2027, 8, 3));
        runJobOn(LocalDate.of(2027, 9, 1));

        assertThat(summaryBusinessDatesOf(student)).containsExactly(LocalDate.of(2027, 8, 3), LocalDate.of(2027, 9, 1));
        assertThat(summaryMonthsOf(student)).containsExactly(LocalDate.of(2027, 8, 1), LocalDate.of(2027, 9, 1));
    }

    @Test
    @Tag("D-338")
    void aMonthWhoseWholeMonthPassedWithoutTheJobIsNotRecoveredInTheFollowingMonth() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        runJobOn(LocalDate.of(2027, 9, 1));
        runJobOn(LocalDate.of(2027, 9, 2));

        assertThat(summaryMonthsOf(student)).containsExactly(LocalDate.of(2027, 9, 1));
    }

    @Test
    @Tag("D-338")
    void aStudentWhoPaidEverythingBeforeTheRecoveryDayGetsNoRecoveredSummary() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        Invoice invoice = installmentDueOn(enrollment, JULY_TENTH);
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(invoice, LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 2));

        runJobOn(LocalDate.of(2027, 8, 3));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void aReceiptUnderReviewOnTheRecoveryDaySuppressesTheRecoveredSummary() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        putReceiptUnderReview(installmentDueOn(enrollment, JULY_TENTH));
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        runJobOn(LocalDate.of(2027, 8, 3));

        assertThat(summariesOf(student)).isZero();
    }

    @Test
    @Tag("D-338")
    void aDebtThatBecameOverdueAfterTheSummaryDayDoesNotTriggerARecoveredSummaryOfThatMonth() {
        Student student = newStudent();
        CourseEnrollment enrollment = newCourseEnrollment(student);
        installmentDueOn(enrollment, LocalDate.of(2027, 8, 20));
        withdrawVoluntarily(enrollment, JULY_THIRTY_FIRST);

        runJobOn(LocalDate.of(2027, 8, 2));
        runJobOn(LocalDate.of(2027, 8, 23));

        assertThat(summariesOf(student)).isZero();
    }
}
