package com.red_escuela.backend.billing;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DebtorsQueryCountTest extends AbstractIntegrationTest {
    private static final int MANY = 12;
    private static final int MAX_QUERIES = 12;
    private static final int PAGE_SIZE = 25;
    private static final int PAGE_SIZE_MAX = 100;

    @Autowired
    private ListDebtorsUseCase listDebtors;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

    private int sequence = 0;

    private Student persistStudent(Institution institution) {
        sequence++;
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername("debtor.count." + System.nanoTime() + "." + sequence);
        person.setDni(Long.toString(60000000L + System.nanoTime() % 30000000L + sequence));
        person.setName("Deudor");
        person.setLastname("Numero" + sequence);
        person.setEmail(person.getUsername() + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Student student = new Student();
        student.setPerson(personRepository.save(person));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Set<Long> persistDebtors(int count, long amountStep) {
        Institution institution = new Institution();
        institution.setName("Instituto deudores " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Student student = persistStudent(institution);
            Course course = new Course();
            course.setName("Curso deuda " + System.nanoTime() + "-" + i);
            course.setTerm(term);
            course.setIsActive(StatusType.ACTIVE);
            course = courseRepository.save(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            enrollment = courseEnrollmentRepository.save(enrollment);
            YearMonth period = YearMonth.now().minusMonths(2);
            Money amount = ars(1000 + amountStep * i);
            BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(period).dueOn(period.atDay(10))
                    .withBaseAmount(amount).inState(InvoicePaymentStatus.OVERDUE).build();
            ids.add(student.getId());
        }
        entityManager.flush();
        entityManager.clear();
        return Set.copyOf(ids);
    }

    @Test
    void listDebtorsExecutesConstantQueriesForManyStudents() {
        Set<Long> studentIds = persistDebtors(MANY, 100);

        PageResponse<DebtorSummaryResponse> page = QueryCountAssertions.assertAtMostQueries(
                entityManagerFactory, MAX_QUERIES, () -> listDebtors.list("DEBT_AGE", 0, PAGE_SIZE_MAX));

        assertThat(page.items()).extracting(DebtorSummaryResponse::studentId).containsAll(studentIds);
    }

    @Test
    void listDebtorsPagesAfterAggregationKeepingTotals() {
        Set<Long> studentIds = persistDebtors(30, 100);
        long totalBefore = listDebtors.list("DEBT_AMOUNT", 0, PAGE_SIZE_MAX).totalElements();
        assertThat(totalBefore).isGreaterThanOrEqualTo(30);

        PageResponse<DebtorSummaryResponse> first = listDebtors.list("DEBT_AMOUNT", 0, PAGE_SIZE);
        PageResponse<DebtorSummaryResponse> second = listDebtors.list("DEBT_AMOUNT", 1, PAGE_SIZE);

        assertThat(first.items()).hasSize(PAGE_SIZE);
        assertThat(first.totalElements()).isEqualTo(totalBefore);
        assertThat(second.items()).hasSize((int) totalBefore - PAGE_SIZE);
        assertThat(second.page()).isEqualTo(1);
        assertThat(studentIds).isNotEmpty();
    }

    @Test
    void listDebtorsSortsByDescendingAmountWithinCurrency() {
        persistDebtors(MANY, 100);

        List<DebtorSummaryResponse> items = listDebtors.list("DEBT_AMOUNT", 0, PAGE_SIZE_MAX).items();

        for (int i = 1; i < items.size(); i++) {
            DebtorSummaryResponse previous = items.get(i - 1);
            DebtorSummaryResponse current = items.get(i);
            if (previous.totalDebt().currency() == current.totalDebt().currency()) {
                assertThat(previous.totalDebt().amount()).isGreaterThanOrEqualTo(current.totalDebt().amount());
            }
        }
    }

    @Test
    void listDebtorsKeepsDebtAgeOrderingByDefault() {
        persistDebtors(MANY, 100);

        List<DebtorSummaryResponse> items = listDebtors.list("DEBT_AGE", 0, PAGE_SIZE_MAX).items();

        for (int i = 1; i < items.size(); i++) {
            assertThat(items.get(i - 1).debtAgeDays()).isGreaterThanOrEqualTo(items.get(i).debtAgeDays());
        }
    }

    @Test
    void listDebtorsBoundsPageSizeAndNegativePage() {
        persistDebtors(MANY, 100);

        PageResponse<DebtorSummaryResponse> page = listDebtors.list("DEBT_AGE", -3, 100000);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PAGE_SIZE_MAX);
    }
}
