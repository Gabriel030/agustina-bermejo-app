package com.red_escuela.backend.graduation.certificate.regular;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ApproveCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CreateDirectCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificateRequestsUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RejectCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RequestCertificateExceptionUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RegularRequestDebtDataIntegrationTest extends AbstractIntegrationTest {

    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";
    private static final String DECISION = "Reclamo verificado con el expediente";
    private static final int STUDENTS = 20;

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private RegularCertificateRequestRepository requestRepository;
    @Autowired
    private ListRegularCertificateRequestsUseCase listUseCase;
    @Autowired
    private RequestCertificateExceptionUseCase requestExceptionUseCase;
    @Autowired
    private ApproveCertificateExceptionUseCase approveUseCase;
    @Autowired
    private RejectCertificateExceptionUseCase rejectUseCase;
    @Autowired
    private CreateDirectCertificateExceptionUseCase createDirectUseCase;
    @MockitoSpyBean
    private StudentOverdueDebtFinder debtFinder;

    private Person staff;
    private Person otherStaff;
    private Person director;
    private String staffToken;
    private String otherStaffToken;
    private String directorToken;
    private final List<Long> studentIds = new ArrayList<>();
    private final List<Long> enrollmentIds = new ArrayList<>();

    @BeforeEach
    void seedTwentyStudentsWithPendingRequests() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        setBillingMode("FULL");
        jdbc.update("delete from certificate_exception_status_change");
        jdbc.update("delete from certificate_exception_approval");
        jdbc.update("delete from regular_certificate_request");
        studentIds.clear();
        enrollmentIds.clear();
        transactions.executeWithoutResult(status -> {
            staff = persistPerson("ADMINISTRATIVO");
            otherStaff = persistPerson("ADMINISTRATIVO");
            director = persistPerson("DIRECTOR");
            staffToken = jwtService.generateAccessToken(staff);
            otherStaffToken = jwtService.generateAccessToken(otherStaff);
            directorToken = jwtService.generateAccessToken(director);
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            for (int index = 0; index < STUDENTS; index++) {
                Student student = new Student();
                student.setPerson(fixtures.person());
                student.setInstitution(institution);
                student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
                entityManager.persist(student);
                ProgramEnrollment enrollment = new ProgramEnrollment();
                enrollment.setStudent(student);
                enrollment.setProgram(fixtures.program());
                enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
                enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
                entityManager.persist(enrollment);
                entityManager.flush();
                studentIds.add(student.getId());
                enrollmentIds.add(enrollment.getId());
                if (index % 2 == 0) {
                    BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                            .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
                    BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                            .forPeriod(YearMonth.of(2026, 4)).dueOn(LocalDate.of(2026, 4, 10)).withBaseAmount(ars(15000)).build();
                    BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                            .forPeriod(YearMonth.of(2027, 1)).dueOn(LocalDate.of(2027, 1, 10)).withBaseAmount(ars(15000)).build();
                }
                requestRepository.save(RegularCertificateRequest.record(RegularityKind.PROGRAM, enrollment.getId(), student.getId(),
                        staff.getId(), LocalDateTime.of(2026, 11, 9, 10, index), RegularCertificateChannel.STAFF,
                        RegularRequestOutcome.PENDING_STAFF, List.of("PAYMENT_STANDING")));
            }
        });
        actAs(staff);
        clearInvocations(debtFinder);
    }

    @AfterEach
    void restoreBillingModeAndSecurity() {
        SecurityContextHolder.clearContext();
        setBillingMode("FULL");
    }

    private void actAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("debt." + suffix);
        person.setEmail("debt." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private RegularCertificateRequestResponse rowOf(List<RegularCertificateRequestResponse> rows, int index) {
        return rows.stream().filter(row -> row.studentId().equals(studentIds.get(index))).findFirst().orElseThrow();
    }

    private CertificateExceptionResponse requestExceptionFor(int index) {
        return requestExceptionUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentIds.get(index),
                OrganismKind.COURT, "Causa 77/2026", REASON));
    }

    private long statementsOf(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void listingTwentyRequestsReadsDebtWithASingleBatchQuery() {
        long oneStudent = statementsOf(() -> assertThat(listUseCase.list(studentIds.get(0), null)).hasSize(1));
        clearInvocations(debtFinder);

        long twentyStudents = statementsOf(() -> assertThat(listUseCase.list(null, null)).hasSize(STUDENTS));

        assertThat(twentyStudents).isLessThanOrEqualTo(oneStudent);
        verify(debtFinder, times(1)).summarizeArsOverdueDebt(any(), any());
    }

    @Test
    void listingExposesTotalAndOverdueInstallmentsPerStudent() {
        List<RegularCertificateRequestResponse> rows = listUseCase.list(null, null);

        RegularCertificateRequestResponse indebted = rowOf(rows, 0);
        assertThat(indebted.hasDebt()).isTrue();
        assertThat(indebted.totalDue().amount()).isEqualByComparingTo("30000");
        assertThat(indebted.overdueInstallments()).isEqualTo(2);
        RegularCertificateRequestResponse settled = rowOf(rows, 1);
        assertThat(settled.hasDebt()).isFalse();
        assertThat(settled.totalDue().amount()).isEqualByComparingTo("0");
        assertThat(settled.overdueInstallments()).isZero();
        assertThat(settled.exceptionId()).isNull();
    }

    @Test
    void listingReturnsNullDebtWhenBillingIsOff() {
        setBillingMode("OFF");

        List<RegularCertificateRequestResponse> rows = listUseCase.list(null, null);

        assertThat(rows).hasSize(STUDENTS);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.totalDue()).isNull();
            assertThat(row.overdueInstallments()).isNull();
            assertThat(row.hasDebt()).isNull();
        });
        verify(debtFinder, times(0)).summarizeArsOverdueDebt(any(), any());
    }

    @Test
    void listingShowsOpenExceptionButNotClosedOnes() {
        CertificateExceptionResponse pending = requestExceptionFor(0);
        CertificateExceptionResponse toApprove = requestExceptionFor(2);
        CertificateExceptionResponse toReject = requestExceptionFor(4);
        actAs(director);
        approveUseCase.approve(toApprove.id(), DECISION);
        rejectUseCase.reject(toReject.id(), DECISION);
        actAs(staff);

        List<RegularCertificateRequestResponse> rows = listUseCase.list(null, null);

        assertThat(rowOf(rows, 0).exceptionId()).isEqualTo(pending.id());
        assertThat(rowOf(rows, 0).exceptionStatus()).isEqualTo(CertificateExceptionStatus.PENDING);
        assertThat(rowOf(rows, 0).exceptionValidUntil()).isNull();
        assertThat(rowOf(rows, 2).exceptionId()).isEqualTo(toApprove.id());
        assertThat(rowOf(rows, 2).exceptionStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(rowOf(rows, 2).exceptionValidUntil()).isNotNull();
        assertThat(rowOf(rows, 4).exceptionId()).isNull();
        assertThat(rowOf(rows, 4).exceptionStatus()).isNull();
    }

    @Test
    void exceptionStoresTheDebtAtTheMomentOfTheRequest() {
        CertificateExceptionResponse created = requestExceptionFor(0);

        assertThat(created.debtTotalAtRequest().amount()).isEqualByComparingTo("30000");
        assertThat(created.overdueInstallmentsAtRequest()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select debt_total_at_request from certificate_exception_approval where id = ?", java.math.BigDecimal.class,
                created.id())).isEqualByComparingTo("30000");
        assertThat(jdbc.queryForObject("select overdue_installments_at_request from certificate_exception_approval where id = ?", Integer.class,
                created.id())).isEqualTo(2);
    }

    @Test
    void exceptionKeepsNullDebtWhenBillingWasOffAtTheRequest() {
        setBillingMode("OFF");

        CertificateExceptionResponse created = requestExceptionFor(0);

        assertThat(created.debtTotalAtRequest()).isNull();
        assertThat(created.overdueInstallmentsAtRequest()).isNull();
    }

    @Test
    void summaryCountsOnlyOwnExceptionsForAdministrativeAndAllForDirector() throws Exception {
        CertificateExceptionResponse first = requestExceptionFor(0);
        requestExceptionFor(2);
        actAs(otherStaff);
        requestExceptionFor(4);
        actAs(director);
        approveUseCase.approve(first.id(), DECISION);

        mockMvc.perform(get("/certificate-exceptions/summary").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.rejected").value(0)).andExpect(jsonPath("$.withdrawn").value(0))
                .andExpect(jsonPath("$.expired").value(0)).andExpect(jsonPath("$.consumed").value(0));
        mockMvc.perform(get("/certificate-exceptions/summary").header("Authorization", "Bearer " + otherStaffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.approved").value(0));
        mockMvc.perform(get("/certificate-exceptions/summary").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(2)).andExpect(jsonPath("$.approved").value(1));
    }

    private Long addInvoice(int index, YearMonth period, LocalDate dueDate, Money amount, InvoicePaymentStatus status) {
        return transactions.execute(tx -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentIds.get(index));
            Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(period).dueOn(dueDate).withBaseAmount(amount).inState(status).build();
            return invoice.getId();
        });
    }

    private RegularCertificateRequestResponse studentRow(int index) {
        return rowOf(listUseCase.list(null, null), index);
    }

    @Test
    void onlyUpcomingInstallmentsAreNotDebt() {
        addInvoice(1, YearMonth.of(2026, 12), LocalDate.of(2026, 12, 10), ars(15000), InvoicePaymentStatus.PENDING);
        addInvoice(1, YearMonth.of(2027, 1), LocalDate.of(2027, 1, 10), ars(15000), InvoicePaymentStatus.PENDING);

        RegularCertificateRequestResponse row = studentRow(1);

        assertThat(row.hasDebt()).isFalse();
        assertThat(row.overdueInstallments()).isZero();
        assertThat(row.totalDue().amount()).isEqualByComparingTo("0");
    }

    @Test
    void installmentDueTodayIsNotDebtButTheOneDueYesterdayIs() {
        addInvoice(1, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 10), ars(15000), InvoicePaymentStatus.PENDING);
        assertThat(studentRow(1).hasDebt()).isFalse();

        addInvoice(1, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 9), ars(7000), InvoicePaymentStatus.PENDING);

        RegularCertificateRequestResponse row = studentRow(1);
        assertThat(row.hasDebt()).isTrue();
        assertThat(row.overdueInstallments()).isEqualTo(1);
        assertThat(row.totalDue().amount()).isEqualByComparingTo("7000");
    }

    @Test
    void paidCancelledDraftAndAwaitingPaymentInstallmentsAreNotDebt() {
        addInvoice(1, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), ars(15000), InvoicePaymentStatus.PAID);
        Long cancelled = addInvoice(1, YearMonth.of(2026, 4), LocalDate.of(2026, 4, 10), ars(15000), InvoicePaymentStatus.PENDING);
        Long draft = addInvoice(1, YearMonth.of(2026, 5), LocalDate.of(2026, 5, 10), ars(15000), InvoicePaymentStatus.PENDING);
        Long awaiting = addInvoice(1, YearMonth.of(2026, 6), LocalDate.of(2026, 6, 10), ars(15000), InvoicePaymentStatus.PENDING);
        jdbc.update("update invoice set payment_status = 'CANCELLED', paid_amount = 0, outstanding_amount = 0, credited_amount = total where id = ?", cancelled);
        jdbc.update("update invoice set document_status = 'DRAFT', payment_status = null, invoice_number = null, issue_date = null, due_date = null, student_snapshot = null where id = ?", draft);
        jdbc.update("update invoice set document_status = 'AWAITING_PAYMENT', payment_status = null, invoice_number = null, issue_date = null, due_date = null, student_snapshot = null, invoice_type = 'ANNUAL_UPFRONT', payment_deadline = date '2026-12-31' where id = ?", awaiting);

        RegularCertificateRequestResponse row = studentRow(1);

        assertThat(row.hasDebt()).isFalse();
        assertThat(row.totalDue().amount()).isEqualByComparingTo("0");
    }

    @Test
    void installmentUnderReviewWithoutAProtectingReceiptCountsAsDebtLikeTheGate() {
        Long underReview = addInvoice(1, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), ars(15000), InvoicePaymentStatus.PENDING);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", underReview);

        RegularCertificateRequestResponse row = studentRow(1);

        assertThat(row.hasDebt()).isTrue();
        assertThat(row.totalDue().amount()).isEqualByComparingTo("15000");
        assertThat(debtFinder.hasOverdueDebt(studentIds.get(1), LocalDate.of(2026, 11, 10))).isTrue();
    }

    @Test
    void foreignCurrencyDebtIsNeverSummedAsPesos() {
        addInvoice(1, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), usd(100), InvoicePaymentStatus.PENDING);
        assertThat(studentRow(1).hasDebt()).isFalse();
        addInvoice(1, YearMonth.of(2026, 4), LocalDate.of(2026, 4, 10), ars(15000), InvoicePaymentStatus.PENDING);

        RegularCertificateRequestResponse row = studentRow(1);

        assertThat(row.totalDue().amount()).isEqualByComparingTo("15000");
        assertThat(row.totalDue().currency().name()).isEqualTo("ARS");
        assertThat(row.overdueInstallments()).isEqualTo(1);
    }

    @Test
    void directExceptionByTheDirectorAlsoStoresTheDebtAtRequest() {
        actAs(director);

        CertificateExceptionResponse created = createDirectUseCase.create(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM,
                enrollmentIds.get(0), OrganismKind.COURT, "Causa 77/2026", REASON));

        assertThat(created.createdByDirector()).isTrue();
        assertThat(created.debtTotalAtRequest().amount()).isEqualByComparingTo("30000");
        assertThat(created.overdueInstallmentsAtRequest()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select debt_currency_at_request from certificate_exception_approval where id = ?", String.class,
                created.id())).isEqualTo("ARS");
    }
}
