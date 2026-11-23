package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
abstract class EnrollmentFeeP4Support extends AbstractIntegrationTest {
    private static final AtomicLong DNI = new AtomicLong(300_000_000L + ThreadLocalRandom.current().nextLong(0, 90_000_000L));

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected JwtService jwtService;

    protected record Career(Long programId, Long directorId, Long subjectId, Long institutionId) {
    }

    protected record Year(Long templateId, Long termId, int year) {
    }

    protected record World(Career career, Year year, Term firstTerm, Term secondTerm, Long firstSection, Long secondSection, Long enrollment) {
    }

    protected static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    protected void today(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
    }

    protected World worldWithFees(int feeCount, Integer secondFeeMonth) {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, feeCount, 2, secondFeeMonth);
        Term first = newTerm(2027, 3, date(2027, 2, 1), date(2027, 3, 10), null);
        Term second = newTerm(2027, 8, date(2027, 7, 1), date(2027, 8, 10), null);
        Long firstSection = newSection(career, first);
        Long secondSection = newSection(career, second);
        today(date(2027, 2, 5));
        Long enrollment = newEnrollment(career, YearMonth.of(2027, 1), date(2027, 1, 20));
        return new World(career, year, first, second, firstSection, secondSection, enrollment);
    }

    protected Person newPerson() {
        String dni = String.valueOf(DNI.incrementAndGet());
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    protected Career newCareer() {
        return transactionTemplate.execute(status -> {
            Person director = newPerson();
            Institution institution = new Institution();
            institution.setName("Instituto");
            entityManager.persist(institution);
            Program program = new Program();
            program.setVisibleInCatalog(true);
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);
            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia " + System.nanoTime());
            entityManager.persist(subject);
            ProgramSubject programSubject = new ProgramSubject();
            programSubject.setProgram(program);
            programSubject.setSubject(subject);
            programSubject.setCredits(10);
            programSubject.setCurricularYear(1);
            programSubject.setCurricularTerm(CurricularTerm.FIRST);
            programSubject.setStatus(StatusType.ACTIVE);
            entityManager.persist(programSubject);
            PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.MONTHLY_INSTALLMENT, "Precio", ars(10000), LocalDate.of(2020, 1, 1), director.getId());
            PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Precio", ars(5000), LocalDate.of(2020, 1, 1), director.getId());
            return new Career(program.getId(), director.getId(), subject.getId(), institution.getId());
        });
    }

    protected Year newYear(Career career, int year, int installments, int feeCount, int firstFeeMonth, Integer secondFeeMonth) {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(year);
            term.setDescription(String.valueOf(year));
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(entityManager.getReference(Program.class, career.programId()));
            template.setTerm(term);
            TestTemplateTerms.setRecurringFeeInvoicesCount(template, installments);
            TestTemplateTerms.configureEnrollmentFees(template, feeCount, firstFeeMonth, secondFeeMonth);
            template.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(template);
            TestTemplateTerms.publish(entityManager, template);
            return new Year(template.getId(), term.getId(), year);
        });
    }

    protected void configureYear(Year year, Consumer<ProgramYearBillingTemplate> change) {
        transactionTemplate.executeWithoutResult(status -> {
            ProgramYearBillingTemplate template = entityManager.find(ProgramYearBillingTemplate.class, year.templateId());
            TestTemplateTerms.load(entityManager, template);
            change.accept(template);
            TestTemplateTerms.publish(entityManager, template);
        });
    }

    protected Long newEnrollment(Career career, YearMonth planFrom, LocalDate enrolledOn) {
        return transactionTemplate.execute(status -> {
            Person person = newPerson();
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(entityManager.getReference(Institution.class, career.institutionId()));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = entityManager.getReference(Program.class, career.programId());
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(enrolledOn.atTime(10, 0));
            entityManager.persist(enrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Plan");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(option);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(planFrom);
            selection.setScopeMonths(10);
            selection.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(selection);
            return enrollment.getId();
        });
    }

    protected Term newTerm(int year, int startMonth, LocalDate windowOpens, LocalDate windowCloses, TermType type) {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(year);
            term.setDescription(year + "-" + type);
            term.setType(type);
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(year, startMonth, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(year, startMonth + 4, 28, 23, 59));
            term.setEnrollmentStart(windowOpens.atStartOfDay());
            term.setEnrollmentEnd(windowCloses.atTime(23, 59));
            entityManager.persist(term);
            return term;
        });
    }

    protected Long newSection(Career career, Term term) {
        return transactionTemplate.execute(status -> {
            SubjectSection section = new SubjectSection();
            section.setName("Com " + System.nanoTime());
            section.setSubject(entityManager.getReference(Subject.class, career.subjectId()));
            section.setTerm(entityManager.getReference(Term.class, term.getId()));
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            return section.getId();
        });
    }

    protected Long newSectionEnrollment(Long enrollmentId, Long sectionId, EnrollmentStatus statusValue) {
        return transactionTemplate.execute(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(enrollment.getStudent());
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(entityManager.getReference(SubjectSection.class, sectionId));
            sectionEnrollment.setStatus(statusValue);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2027, 3, 1, 10, 0));
            sectionEnrollment.setAttemptNumber(1);
            entityManager.persist(sectionEnrollment);
            return sectionEnrollment.getId();
        });
    }

    protected List<Invoice> enrollmentFeeInvoices(Long enrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .filter(invoice -> invoice.getFeeType() == FeeType.ENROLLMENT_FEE).toList();
    }

    protected void payAllEnrollmentFees(Long enrollmentId) {
        enrollmentFeeInvoices(enrollmentId).forEach(BillingTestFixtures::payInvoiceInFull);
    }

    protected void markAllEnrollmentFeesOverdue(Long enrollmentId) {
        enrollmentFeeInvoices(enrollmentId).forEach(BillingTestFixtures::markInvoiceAsOverdue);
    }

    protected InvoicePaymentStatus paymentStatusOf(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow().getPaymentStatus();
    }

    protected String tokenWithRole(String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = newPerson();
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            entityManager.flush();
            return jwtService.generateAccessToken(person);
        });
    }

    protected String studentToken(Long enrollmentId) {
        return transactionTemplate.execute(status -> {
            Person person = entityManager.find(ProgramEnrollment.class, enrollmentId).getStudent().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            entityManager.flush();
            return jwtService.generateAccessToken(person);
        });
    }

    protected ResultActions grantExemption(String token, Long enrollmentId, Long templateId, int slot, String reason) throws Exception {
        return mockMvc.perform(post("/program-enrollments/{id}/enrollment-fee-exemptions", enrollmentId)
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token)
                .content("{\"templateId\":" + templateId + ",\"slot\":" + slot + ",\"reason\":\"" + reason + "\"}"));
    }

    protected ResultActions revokeExemption(String token, Long exemptionId, String notes) throws Exception {
        return mockMvc.perform(post("/enrollment-fee-exemptions/{id}/revoke", exemptionId).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content("{\"notes\":\"" + notes + "\"}"));
    }

    protected ResultActions feeStatus(String token, Long enrollmentId) throws Exception {
        return mockMvc.perform(get("/program-enrollments/{id}/enrollment-fee-status", enrollmentId).header("Authorization", "Bearer " + token));
    }

    protected ResultActions exemptionList(String token, Long enrollmentId) throws Exception {
        return mockMvc.perform(get("/program-enrollments/{id}/enrollment-fee-exemptions", enrollmentId).header("Authorization", "Bearer " + token));
    }

    protected long countExemptions(Long enrollmentId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                "select count(e) from EnrollmentFeeExemption e where e.programEnrollment.id = :id", Long.class)
                .setParameter("id", enrollmentId).getSingleResult());
    }
}
