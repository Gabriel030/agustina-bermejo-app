package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpact;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;
import com.red_escuela.backend.billing.collections.usecase.GetCollectionReminderImpactUseCase;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderImpactIT extends AbstractReminderTimelineIT {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 2);

    @Autowired
    private GetCollectionReminderImpactUseCase impactUseCase;
    @Autowired
    private BillingConfigurationReader configurationReader;

    @BeforeEach
    void setClockToTheDayBeforeTheSimulation() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(TODAY, LocalTime.NOON));
    }

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private static ReminderSchedule scheduleWithBeforeDueAt(int offsetDays) {
        List<ReminderNotice> notices = ReminderSchedule.initial().notices().stream()
                .map(notice -> notice.noticeKey().equals("before-due")
                        ? new ReminderNotice(notice.noticeKey(), ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, offsetDays, EnumSet.allOf(ReminderChannel.class), null)
                        : notice)
                .toList();
        return new ReminderSchedule(false, notices, ReminderSendingOverrides.none());
    }

    private ReminderScheduleProposal courseProposal(Course course, int beforeDueOffset) {
        return new ReminderScheduleProposal(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, course.getId()), scheduleWithBeforeDueAt(beforeDueOffset), null);
    }

    private List<Invoice> studentsWithAnInstallment(Course course, int students, LocalDate dueDate) {
        List<Invoice> invoices = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            Student student = newStudent();
            CourseEnrollment enrollment = enrollInCourse(student, course);
            invoices.add(installmentOf(enrollment, march(1).minusDays(3), dueDate));
        }
        return invoices;
    }

    @Test
    @Tag("D-350")
    @Tag("D-368")
    void fortyStudentsWithAnOpenInstallmentSeeTheirNoticeMoveWhenTheProposalPutsItTwoDaysBefore() {
        Course course = newCourse();
        studentsWithAnInstallment(course, 40, march(11));

        CollectionReminderImpact moved = impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(4)));
        CollectionReminderImpact same = impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -1), march(4)));

        assertThat(same.studentsWithChangedSchedule()).isZero();
        assertThat(moved.studentsWithChangedSchedule()).isEqualTo(40);
    }

    @Test
    @Tag("D-350")
    void onlyStudentsWithAnOpenInstallmentInsideTheWindowAreCounted() {
        Course course = newCourse();
        studentsWithAnInstallment(course, 3, march(11));
        studentsWithAnInstallment(course, 2, LocalDate.of(2027, 6, 11));
        List<Invoice> paid = studentsWithAnInstallment(course, 2, march(11));
        paid.forEach(invoice -> pay(invoice, march(1)));

        CollectionReminderImpact impact = impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(4)));

        assertThat(impact.studentsWithChangedSchedule()).isEqualTo(3);
    }

    @Test
    @Tag("D-350")
    void theWindowStartsAtTheEffectiveDateSoAChangeThatAlreadyHappenedDoesNotCount() {
        Course course = newCourse();
        studentsWithAnInstallment(course, 3, march(11));

        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(20))).studentsWithChangedSchedule()).isZero();
        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(9))).studentsWithChangedSchedule()).isEqualTo(3);
        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(10))).studentsWithChangedSchedule()).isEqualTo(3);
        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(11))).studentsWithChangedSchedule()).isZero();
    }

    @Test
    @Tag("D-350")
    void anEffectiveDateInThePastStartsTheWindowTomorrow() {
        Course course = newCourse();
        studentsWithAnInstallment(course, 3, march(11));

        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(course, -2), march(1))).studentsWithChangedSchedule()).isEqualTo(3);
    }

    @Test
    @Tag("D-350")
    void aProposalOnOneCourseDoesNotTouchTheStudentsOfAnotherCourse() {
        Course proposed = newCourse();
        Course other = newCourse();
        studentsWithAnInstallment(proposed, 2, march(11));
        studentsWithAnInstallment(other, 5, march(11));

        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(courseProposal(proposed, -2), march(4))).studentsWithChangedSchedule()).isEqualTo(2);
    }

    @Test
    @Tag("D-350")
    void aGroupProposalReachesCoursesWithoutExceptionButNotThoseWithTheirOwn() {
        Course followsGroup = newCourse();
        Course withException = newCourse();
        publishCourseSchedule(withException, march(3), scheduleWithBeforeDueAt(-1).notices());
        studentsWithAnInstallment(followsGroup, 2, march(11));
        studentsWithAnInstallment(withException, 4, march(11));
        ReminderScheduleProposal groupProposal = new ReminderScheduleProposal(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, null), scheduleWithBeforeDueAt(-2), null);

        CollectionReminderImpact impact = impactUseCase.getImpactOf(new CollectionReminderImpactQuery(groupProposal, march(4)));

        assertThat(impact.studentsWithChangedSchedule()).isGreaterThanOrEqualTo(2);
        ResolvedReminderSchedule untouchedException = configurationReader.reminderScheduleWithProposal(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, withException.getId()),
                march(4), groupProposal);
        ResolvedReminderSchedule reached = configurationReader.reminderScheduleWithProposal(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, followsGroup.getId()),
                march(4), groupProposal);
        assertThat(untouchedException.notices()).anyMatch(notice -> notice.noticeKey().equals("before-due") && notice.offsetDays() == -1);
        assertThat(reached.notices()).anyMatch(notice -> notice.noticeKey().equals("before-due") && notice.offsetDays() == -2);
    }

    @Test
    @Tag("D-350")
    void turningTheDailyDigestOffCountsTheStudentsWithSomethingToSendInTheWindow() {
        Course course = newCourse();
        studentsWithAnInstallment(course, 3, march(11));
        CollectionReminderSettings current = configurationReader.collectionReminderSettingsEffectiveOn(march(4));
        CollectionReminderSettings digestToggled = new CollectionReminderSettings(current.suspensionNoticeEnabled(), current.postWithdrawalSummaryMonths(), current.emailWindowStart(),
                current.emailWindowEnd(), current.dailyEmailCap(), current.contactListDaysOverdue(), current.contactListOverdueInstallments(), !current.dailyDigestEnabled());
        ReminderScheduleProposal proposal = new ReminderScheduleProposal(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, course.getId()),
                scheduleWithBeforeDueAt(-1), digestToggled);

        assertThat(impactUseCase.getImpactOf(new CollectionReminderImpactQuery(proposal, march(4))).studentsWithChangedSchedule()).isGreaterThanOrEqualTo(3);
    }
}
