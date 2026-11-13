package com.red_escuela.backend.academicstructure.service;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceResolver;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OfferingPriceSingleSourceIT extends AbstractIntegrationTest {

    private static final LocalDate CREATION_DAY = LocalDate.of(2027, 3, 1);
    private static final LocalDate INCREASE_DAY = LocalDate.of(2027, 6, 1);

    @Autowired
    private ProgramService programService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private OfferingListPriceReader listPriceReader;
    @Autowired
    private OfferingPriceResolver priceResolver;
    @Autowired
    private PriceReader priceReader;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void authenticateAStaffMemberOnTheCreationDay() {
        MutableClock.INSTANCE.setDate(CREATION_DAY);
        Long staffId = transactions.execute(status -> {
            Person staff = new CycleTestFixtures(entityManager).person();
            entityManager.flush();
            return staff.getId();
        });
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staffId.toString(), null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Program createProgram() {
        return programService.create(new CreateProgramRequest("Carrera " + UUID.randomUUID(), 3, ars("1000"), ars("500"), 100, null, null, null, null, null, null));
    }

    private void raiseProgramPrices(Long programId) {
        MutableClock.INSTANCE.setDate(INCREASE_DAY);
        UpdateProgramRequest request = new UpdateProgramRequest();
        request.setPrice(ars("1200"));
        request.setEnrollmentFeeAmount(ars("650"));
        programService.update(programId, request);
    }

    private Long issueMonthlyInstallmentOf(Long programId) {
        return transactions.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String suffix = UUID.randomUUID().toString();
            person.setName("Persona");
            person.setLastname(suffix);
            person.setDni(suffix.substring(0, 12));
            person.setUsername("price." + suffix);
            person.setEmail("price." + suffix + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(entityManager.find(Program.class, programId));
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(enrollment);
            entityManager.flush();
            return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.of(2027, 3))
                    .dueOn(LocalDate.of(2027, 3, 10)).withBaseAmount(ars("1000")).build().getId();
        });
    }

    private String billedFingerprintOf(Long invoiceId) {
        return jdbc.queryForObject("select md5(concat_ws('|', "
                + "(select string_agg(i::text, ',' order by i.id) from invoice i where i.id = ?), "
                + "(select string_agg(l::text, ',' order by l.id) from invoice_line l where l.invoice_id = ?), "
                + "(select string_agg(e::text, ',' order by e.id) from current_account_entry e where e.student_current_account_id = "
                + "(select student_current_account_id from invoice where id = ?)), "
                + "(select string_agg(v.id || ':' || v.amount || ':' || v.effective_from, ',' order by v.id) from price_version v "
                + "where v.id in (select price_version_id from invoice_line where invoice_id = ?))))",
                String.class, invoiceId, invoiceId, invoiceId, invoiceId);
    }

    @Test
    void anInvoiceAlreadyBilledKeepsEveryAmountWhenTheOfferingPriceRisesLater() {
        Program program = createProgram();
        Long invoiceId = issueMonthlyInstallmentOf(program.getId());
        String before = billedFingerprintOf(invoiceId);

        raiseProgramPrices(program.getId());

        assertThat(billedFingerprintOf(invoiceId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ?", Long.class, invoiceId)).isEqualTo(1L);
    }

    @Test
    void theResolverFollowsTheTimelineOfTheVersionsAndNotTheLegacyColumns() {
        Program program = createProgram();
        raiseProgramPrices(program.getId());
        OfferingReference offering = OfferingReference.ofProgram(program.getId());

        assertThat(priceResolver.findListPriceOn(offering, OfferingFee.MONTHLY_INSTALLMENT, CREATION_DAY.plusDays(30))).contains(ars("1000"));
        assertThat(priceResolver.findListPriceOn(offering, OfferingFee.ENROLLMENT_FEE, INCREASE_DAY.minusDays(1))).contains(ars("500"));
        assertThat(priceResolver.findCurrentListPrice(offering, OfferingFee.MONTHLY_INSTALLMENT)).contains(ars("1200"));
        assertThat(priceResolver.findCurrentListPrice(offering, OfferingFee.ENROLLMENT_FEE)).contains(ars("650"));
        assertThat(priceResolver.findListPriceOn(offering, OfferingFee.MONTHLY_INSTALLMENT, CREATION_DAY.minusDays(1))).isEmpty();
        assertThat(priceReader.requirePriceVersionEffectiveOn(offering, FeeType.MONTHLY_INSTALLMENT, CREATION_DAY.plusDays(1)).listAmount().value()).isEqualTo(ars("1000"));
    }

    @Test
    void theOfferingColumnsHaveNoWritersAnymore() {
        Program program = createProgram();
        raiseProgramPrices(program.getId());
        Long termId = transactions.execute(status -> {
            Long id = new CycleTestFixtures(entityManager).term(2027).getId();
            entityManager.flush();
            return id;
        });
        Long courseId = courseService.create(new CreateCourseRequest("Curso " + UUID.randomUUID(), termId, null, 10, ars("800"), 6, null, null, null)).getId();
        courseService.update(courseId, new UpdateCourseRequest(null, null, null, ars("900"), null, null, null, null));

        assertThat(jdbc.queryForObject("select count(*) from program where id = ? and (price is not null or enrollment_fee_amount is not null)", Long.class, program.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from course where id = ? and price is not null", Long.class, courseId)).isZero();
        assertThat(listPriceReader.findCourseTuition(courseId)).contains(ars("900"));
    }

    @Test
    void settingTheSamePriceAgainDoesNotPublishAnotherVersion() {
        Program program = createProgram();
        Long priceId = jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", Long.class, program.getId());
        UpdateProgramRequest same = new UpdateProgramRequest();
        same.setPrice(Money.of(new java.math.BigDecimal("1000.00"), com.red_escuela.backend.shared.domain.Currency.ARS));

        programService.update(program.getId(), same);

        assertThat(jdbc.queryForObject("select count(*) from price_version where price_id = ?", Long.class, priceId)).isEqualTo(1L);
    }
}
