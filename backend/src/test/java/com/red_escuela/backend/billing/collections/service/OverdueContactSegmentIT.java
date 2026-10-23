package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.StudentSegment;
import com.red_escuela.backend.billing.api.StudentSegmentMember;
import com.red_escuela.backend.billing.api.StudentSegmentParameter;
import com.red_escuela.backend.billing.collections.domain.OverdueContact;
import com.red_escuela.backend.billing.collections.service.OverdueContactSegment.ContactListCounts;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-339")
class OverdueContactSegmentIT extends AbstractOverdueContactIT {

    private static final PageRequest EVERYONE = PageRequest.of(0, 100000);

    @Autowired
    private OverdueContactSegment segment;

    private List<Long> memberIdsOn(LocalDate asOf) {
        return segment.findContacts(asOf, null, EVERYONE).getContent().stream().map(OverdueContact::studentId).toList();
    }

    private OverdueContact contactOf(Student student, LocalDate asOf) {
        return segment.findContacts(asOf, null, EVERYONE).getContent().stream().filter(contact -> contact.studentId().equals(student.getId())).findFirst().orElseThrow();
    }

    @Test
    @Tag("D-339")
    void onlyTheStudentWithAnOldInvoiceWithoutPaymentOrReceiptIsInTheList() {
        Student a = newStudent();
        installmentDueOn(newCourseEnrollment(a), MARCH_ELEVENTH);
        Student c = newStudent();
        putReceiptUnderReview(installmentDueOn(newCourseEnrollment(c), MARCH_ELEVENTH));
        Student d = newStudent();
        installmentDueOn(newCourseEnrollment(d), LocalDate.of(2027, 3, 19));

        List<Long> members = memberIdsOn(MARCH_TWENTY_FIFTH);

        assertThat(members).contains(a.getId()).doesNotContain(c.getId(), d.getId());
    }

    @Test
    @Tag("D-339")
    void twoOverdueInstallmentsPutTheStudentInTheListBeforeAnyOfThemIsOldEnough() {
        Student e = newStudent();
        installmentDueOn(newCourseEnrollment(e), MARCH_ELEVENTH);
        installmentDueOn(newCourseEnrollment(e), MARCH_ELEVENTH);

        assertThat(memberIdsOn(LocalDate.of(2027, 3, 13))).contains(e.getId());
        assertThat(contactOf(e, LocalDate.of(2027, 3, 13)).oldestDaysOverdue()).isEqualTo(2);
        assertThat(contactOf(e, LocalDate.of(2027, 3, 13)).overdueInstallments()).isEqualTo(2);
        assertThat(memberIdsOn(MARCH_ELEVENTH)).doesNotContain(e.getId());
    }

    @Test
    @Tag("D-339")
    void aStudentWhoPaysLeavesTheListTheSameDay() {
        Student a = newStudent();
        Invoice invoice = installmentDueOn(newCourseEnrollment(a), MARCH_ELEVENTH);
        assertThat(memberIdsOn(MARCH_TWENTY_FIFTH)).contains(a.getId());

        BillingTestFixtures.payInvoiceInFullWithMoneyOf(invoice, LocalDate.of(2027, 3, 26), LocalDate.of(2027, 3, 26));

        assertThat(memberIdsOn(LocalDate.of(2027, 3, 26))).doesNotContain(a.getId());
    }

    @Test
    @Tag("D-339")
    void theAgeThresholdIsInclusiveAtFourteenDays() {
        Student a = newStudent();
        installmentDueOn(newCourseEnrollment(a), MARCH_ELEVENTH);

        assertThat(memberIdsOn(LocalDate.of(2027, 3, 24))).doesNotContain(a.getId());
        assertThat(memberIdsOn(MARCH_TWENTY_FIFTH)).contains(a.getId());
    }

    @Test
    @Tag("D-339")
    void aReceiptUnderReviewDoesNotCountTowardsTheTwoInstallmentsEither() {
        Student student = newStudent();
        installmentDueOn(newCourseEnrollment(student), MARCH_ELEVENTH);
        putReceiptUnderReview(installmentDueOn(newCourseEnrollment(student), MARCH_ELEVENTH));

        assertThat(memberIdsOn(LocalDate.of(2027, 3, 13))).doesNotContain(student.getId());
    }

    @Test
    @Tag("D-339")
    @Tag("D-097")
    void overdueAmountsStayInTheirOwnCurrencyAndTheFilterKeepsOnlyWhoOwesIt() {
        Student both = newStudent();
        installmentDueOn(newCourseEnrollment(both), MARCH_ELEVENTH, ars(100000));
        installmentDueOn(newCourseEnrollment(both), MARCH_ELEVENTH, usd(100));
        Student pesosOnly = newStudent();
        installmentDueOn(newCourseEnrollment(pesosOnly), MARCH_ELEVENTH, ars(50000));

        OverdueContact contact = contactOf(both, MARCH_TWENTY_FIFTH);
        List<Long> owingDollars = segment.findContacts(MARCH_TWENTY_FIFTH, Currency.USD, EVERYONE).getContent().stream().map(OverdueContact::studentId).toList();
        List<Long> owingPesos = segment.findContacts(MARCH_TWENTY_FIFTH, Currency.ARS, EVERYONE).getContent().stream().map(OverdueContact::studentId).toList();

        assertThat(contact.overdueAmounts()).containsExactly(ars(100000), usd(100));
        assertThat(owingDollars).contains(both.getId()).doesNotContain(pesosOnly.getId());
        assertThat(owingPesos).contains(both.getId(), pesosOnly.getId());
    }

    @Test
    @Tag("D-339")
    void theCountOfNewcomersOnlyIncludesWhoCrossesTheThresholdThatDay() {
        LocalDate day = LocalDate.of(2027, 3, 25);
        ContactListCounts before = segment.countContacts(day);
        Student crossingToday = newStudent();
        installmentDueOn(newCourseEnrollment(crossingToday), MARCH_ELEVENTH);
        Student alreadyInYesterday = newStudent();
        installmentDueOn(newCourseEnrollment(alreadyInYesterday), LocalDate.of(2027, 3, 10));

        ContactListCounts after = segment.countContacts(day);

        assertThat(after.total() - before.total()).isEqualTo(2);
        assertThat(after.newlyIn() - before.newlyIn()).isEqualTo(1);
    }

    @Test
    @Tag("D-339")
    void theSegmentIsPublishedWithItsNameItsThresholdsAndAPagedQuery() {
        Student first = newStudent();
        installmentDueOn(newCourseEnrollment(first), LocalDate.of(2027, 1, 5));
        Student second = newStudent();
        installmentDueOn(newCourseEnrollment(second), LocalDate.of(2027, 1, 6));
        StudentSegment studentSegment = segment;

        Page<StudentSegmentMember> firstPage = studentSegment.findMembers(MARCH_TWENTY_FIFTH, PageRequest.of(0, 1));
        List<StudentSegmentParameter> parameters = studentSegment.parameters(MARCH_TWENTY_FIFTH);

        assertThat(studentSegment.name()).isEqualTo("OVERDUE_CONTACT");
        assertThat(parameters).containsExactly(new StudentSegmentParameter("contactListDaysOverdue", "14"),
                new StudentSegmentParameter("contactListOverdueInstallments", "2"));
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(studentSegment.findMembers(MARCH_TWENTY_FIFTH, EVERYONE).getContent())
                .contains(new StudentSegmentMember(first.getId()), new StudentSegmentMember(second.getId()));
    }
}
