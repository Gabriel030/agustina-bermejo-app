package com.red_escuela.backend.billing;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.reporting.service.DebtorPageReader;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
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
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DebtorsPageSqlTest extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Autowired
    private ListDebtorsUseCase listDebtors;
    @Autowired
    private DebtorPageReader pageReader;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private Clock clock;
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

    private Institution institution;
    private Term term;
    private CycleTestFixtures cycleFixtures;
    private int sequence = 0;

    private Student singleCourse;
    private Student twoCourses;
    private Student twoPrograms;
    private Student twoCurrencies;
    private Student notOverdue;
    private Student withoutInvoices;
    private Course singleCourseCourse;
    private Course oldestCourse;
    private Program oldestProgram;

    @BeforeEach
    void seedScenario() {
        MutableClock.INSTANCE.setDate(TODAY);
        cycleFixtures = new CycleTestFixtures(entityManager);
        institution = new Institution();
        institution.setName("Instituto morosos " + System.nanoTime());
        institution = institutionRepository.save(institution);
        term = persistTerm();

        singleCourse = persistStudent("Solo");
        CourseEnrollment single = enroll(singleCourse, "Curso unico");
        singleCourseCourse = single.getCourse();
        overdueOf(single, LocalDate.of(2026, 10, 10), ars(5000));
        BillingTestFixtures.invoice().forCourseEnrollment(single).forPeriod(YearMonth.of(2026, 11)).dueOn(LocalDate.of(2026, 12, 10))
                .withBaseAmount(ars(9999)).inState(InvoicePaymentStatus.PENDING).build();

        twoCourses = persistStudent("Dos");
        CourseEnrollment first = enroll(twoCourses, "Curso B1");
        CourseEnrollment second = enroll(twoCourses, "Curso B2");
        oldestCourse = first.getCourse();
        overdueOf(second, LocalDate.of(2026, 10, 1), ars(2000));
        overdueOf(first, LocalDate.of(2026, 9, 1), ars(3000));

        twoPrograms = persistStudent("Carreras");
        ProgramEnrollment recent = enrollInProgram(twoPrograms);
        ProgramEnrollment old = enrollInProgram(twoPrograms);
        oldestProgram = old.getProgram();
        BillingTestFixtures.invoice().forProgramEnrollment(recent).forPeriod(YearMonth.of(2026, 10)).dueOn(LocalDate.of(2026, 10, 5))
                .withBaseAmount(ars(7000)).inState(InvoicePaymentStatus.OVERDUE).build();
        BillingTestFixtures.invoice().forProgramEnrollment(old).forPeriod(YearMonth.of(2026, 8)).dueOn(LocalDate.of(2026, 8, 1))
                .withBaseAmount(ars(1000)).inState(InvoicePaymentStatus.OVERDUE).build();

        twoCurrencies = persistStudent("Monedas");
        CourseEnrollment mixed = enroll(twoCurrencies, "Curso mixto");
        overdueOf(mixed, LocalDate.of(2026, 10, 15), ars(4000));
        overdueOf(mixed, LocalDate.of(2026, 10, 20), usd(50));

        notOverdue = persistStudent("Gracia");
        CourseEnrollment graceEnrollment = enroll(notOverdue, "Curso gracia");
        BillingTestFixtures.invoice().forCourseEnrollment(graceEnrollment).forPeriod(YearMonth.of(2026, 11)).dueOn(LocalDate.of(2026, 11, 15))
                .withBaseAmount(ars(3500)).inState(InvoicePaymentStatus.PENDING).build();
        BillingTestFixtures.invoice().forCourseEnrollment(graceEnrollment).forPeriod(YearMonth.of(2026, 9)).dueOn(LocalDate.of(2026, 9, 10))
                .withBaseAmount(ars(3500)).inState(InvoicePaymentStatus.PAID).build();

        withoutInvoices = persistStudent("Libre");
        enroll(withoutInvoices, "Curso sin facturas");

        entityManager.flush();
        entityManager.clear();
    }

    private Term persistTerm() {
        Term created = com.red_escuela.backend.testsupport.TermFixtures.newTerm();
        created.setYear(2026);
        created.setStatus(StatusType.ACTIVE);
        return termRepository.save(created);
    }

    private Student persistStudent(String lastNamePrefix) {
        sequence++;
        Person person = new Person();
        person.setUsername("debtor.page." + System.nanoTime() + "." + sequence);
        person.setDni(Long.toString(60000000L + System.nanoTime() % 30000000L + sequence));
        person.setName("Alumno");
        person.setLastname(lastNamePrefix + sequence);
        person.setEmail(person.getUsername() + "@example.com");
        person.setAddress(addressRepository.save(new Address()));
        person.setStatus(StatusType.ACTIVE);
        Student student = new Student();
        student.setPerson(personRepository.save(person));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private CourseEnrollment enroll(Student student, String courseName) {
        Course course = new Course();
        course.setName(courseName + " " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(courseRepository.save(course));
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private ProgramEnrollment enrollInProgram(Student student) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(cycleFixtures.program());
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void overdueOf(CourseEnrollment enrollment, LocalDate dueDate, Money amount) {
        BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(amount).inState(InvoicePaymentStatus.OVERDUE).build();
    }

    private List<DebtorSummaryResponse> allPaged(String sortBy, int size) {
        List<DebtorSummaryResponse> all = new ArrayList<>();
        for (int page = 0; ; page++) {
            PageResponse<DebtorSummaryResponse> current = pageReader.findDebtorsPage(sortBy, page, size);
            all.addAll(current.items());
            if (current.items().isEmpty() || all.size() >= current.totalElements()) {
                return all;
            }
        }
    }

    private Map<String, DebtorSummaryResponse> byStudentAndCurrency(List<DebtorSummaryResponse> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row.studentId() + "/" + row.totalDebt().currency(), Function.identity()));
    }

    private void assertScenarioRows(List<DebtorSummaryResponse> rows) {
        Map<String, DebtorSummaryResponse> byKey = byStudentAndCurrency(rows);
        DebtorSummaryResponse single = byKey.get(singleCourse.getId() + "/ARS");
        assertThat(single.totalDebt()).isEqualTo(ars(5000));
        assertThat(single.debtAgeDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 10, 10), TODAY));
        assertThat(single.enrollmentName()).isEqualTo(singleCourseCourse.getName());
        assertThat(single.fullName()).isEqualTo("Alumno " + singleCourse.getPerson().getLastname());
        assertThat(single.dni()).isEqualTo(singleCourse.getPerson().getDni());

        DebtorSummaryResponse two = byKey.get(twoCourses.getId() + "/ARS");
        assertThat(two.totalDebt()).isEqualTo(ars(5000));
        assertThat(two.debtAgeDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 9, 1), TODAY));
        assertThat(two.enrollmentName()).isEqualTo(oldestCourse.getName());

        DebtorSummaryResponse programs = byKey.get(twoPrograms.getId() + "/ARS");
        assertThat(programs.totalDebt()).isEqualTo(ars(8000));
        assertThat(programs.debtAgeDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 8, 1), TODAY));
        assertThat(programs.enrollmentName()).isEqualTo(oldestProgram.getName());

        assertThat(byKey.get(twoCurrencies.getId() + "/ARS").totalDebt()).isEqualTo(ars(4000));
        assertThat(byKey.get(twoCurrencies.getId() + "/USD").totalDebt()).isEqualTo(usd(50));
        assertThat(byKey.get(twoCurrencies.getId() + "/USD").debtAgeDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 10, 20), TODAY));

        assertThat(byKey).doesNotContainKeys(notOverdue.getId() + "/ARS", withoutInvoices.getId() + "/ARS");
    }

    private List<DebtorSummaryResponse> scenarioOnly(List<DebtorSummaryResponse> rows) {
        List<Long> ids = List.of(singleCourse.getId(), twoCourses.getId(), twoPrograms.getId(), twoCurrencies.getId(), notOverdue.getId(), withoutInvoices.getId());
        return rows.stream().filter(row -> ids.contains(row.studentId())).toList();
    }

    @Test
    void legacyListKeepsTheCharacterizedSemantics() {
        List<DebtorSummaryResponse> rows = scenarioOnly(listDebtors.list("DEBT_AGE"));

        assertThat(rows).hasSize(5);
        assertScenarioRows(rows);
    }

    @Test
    void pagedQueryReturnsTheSameSemanticsAsLegacyList() {
        List<DebtorSummaryResponse> rows = scenarioOnly(allPaged("DEBT_AGE", 100));

        assertThat(rows).hasSize(5);
        assertScenarioRows(rows);
    }

    @Test
    void pagedQueryMatchesLegacyListRowByRowForBothSorts() {
        for (String sortBy : List.of("DEBT_AGE", "DEBT_AMOUNT")) {
            Comparator<DebtorSummaryResponse> normalized = ("DEBT_AMOUNT".equals(sortBy)
                    ? Comparator.<DebtorSummaryResponse, Currency>comparing(row -> row.totalDebt().currency())
                    .thenComparing(row -> row.totalDebt().amount(), Comparator.reverseOrder())
                    : Comparator.comparing(DebtorSummaryResponse::debtAgeDays, Comparator.reverseOrder()))
                    .thenComparing(DebtorSummaryResponse::studentId).thenComparing(row -> row.totalDebt().currency());

            List<DebtorSummaryResponse> legacy = listDebtors.list(sortBy).stream().sorted(normalized).toList();

            assertThat(allPaged(sortBy, 7)).as(sortBy).containsExactlyElementsOf(legacy);
        }
    }

    @Test
    void pagesAreConsecutiveSlicesOfTheSameOrderWithExactTotal() {
        List<DebtorSummaryResponse> full = allPaged("DEBT_AMOUNT", 100);
        PageResponse<DebtorSummaryResponse> firstPage = pageReader.findDebtorsPage("DEBT_AMOUNT", 0, 2);
        PageResponse<DebtorSummaryResponse> secondPage = pageReader.findDebtorsPage("DEBT_AMOUNT", 1, 2);

        assertThat(firstPage.totalElements()).isEqualTo(full.size());
        assertThat(firstPage.items()).containsExactlyElementsOf(full.subList(0, 2));
        assertThat(secondPage.items()).containsExactlyElementsOf(full.subList(2, 4));
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(2);
    }

    @Test
    void pageBeyondTheEndIsEmptyButKeepsTheExactTotal() {
        long total = pageReader.findDebtorsPage("DEBT_AGE", 0, 25).totalElements();

        PageResponse<DebtorSummaryResponse> beyond = pageReader.findDebtorsPage("DEBT_AGE", 500, 25);

        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(total);
        assertThat(total).isGreaterThanOrEqualTo(5);
    }

    @Test
    void sizeAndPageAreBoundedLikeTheLegacyPagination() {
        PageResponse<DebtorSummaryResponse> page = pageReader.findDebtorsPage("DEBT_AGE", -3, 100000);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PageResponse.MAX_SIZE);
        assertThat(pageReader.findDebtorsPage("DEBT_AGE", 0, 0).size()).isEqualTo(1);
    }

    @Test
    void debtAgeFollowsTheClock() {
        MutableClock.INSTANCE.setDate(TODAY.plusDays(10));

        DebtorSummaryResponse single = byStudentAndCurrency(allPaged("DEBT_AGE", 100)).get(singleCourse.getId() + "/ARS");

        assertThat(single.debtAgeDays()).isEqualTo((int) ChronoUnit.DAYS.between(LocalDate.of(2026, 10, 10), TODAY.plusDays(10)));
    }

    @Test
    void pageLoadsNoEntitiesAndRunsAtMostTwoStatementsRegardlessOfPageSize() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (int size : List.of(1, 25, 100)) {
                NamedParameterJdbcTemplate spied = spy(jdbc);
                DebtorPageReader reader = new DebtorPageReader(spied, clock);
                statistics.clear();

                reader.findDebtorsPage("DEBT_AGE", 0, size);

                verify(spied, times(1)).query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class));
                verify(spied, atMost(1)).queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(Class.class));
                assertThat(statistics.getEntityLoadCount()).as("entities loaded, size " + size).isZero();
                assertThat(statistics.getPrepareStatementCount()).as("JPA statements, size " + size).isZero();
            }
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
