package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.entity.ScholarshipScope;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class IssuingScenarioFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong(800_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 90_000_000L));

    public record Offering(Long programId, Long directorId, Long institutionId, Currency currency) {
    }

    public record Learner(Long studentId, Long programEnrollmentId, Long selectionId) {
    }

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public IssuingScenarioFactory(EntityManager entityManager, TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    private Person newPerson() {
        String dni = String.valueOf(SEQUENCE.incrementAndGet());
        Address address = new Address();
        address.setStreet("Calle");
        address.setNumber("123");
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

    public Offering newProgram(Currency currency, long monthlyPrice, long enrollmentFeePrice) {
        return transactionTemplate.execute(status -> {
            Person director = newPerson();
            Institution institution = new Institution();
            institution.setName("Instituto");
            entityManager.persist(institution);
            Program program = new Program();
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(10);
            program.setCurrency(currency);
            entityManager.persist(program);
            addPrice(program, director, FeeType.MONTHLY_INSTALLMENT, currency, monthlyPrice, LocalDate.of(2020, 1, 1));
            addPrice(program, director, FeeType.ENROLLMENT_FEE, currency, enrollmentFeePrice, LocalDate.of(2020, 1, 1));
            return new Offering(program.getId(), director.getId(), institution.getId(), currency);
        });
    }

    public void addPrice(Program program, Person director, FeeType type, Currency currency, long amount, LocalDate from) {
        PriceRevision price = new PriceRevision();
        price.setProgram(program);
        price.setFeeType(type);
        price.setDescription("Precio");
        price.setMonthlyAmount(Money.of(BigDecimal.valueOf(amount), currency));
        price.setEffectiveFrom(from);
        price.setCreatedBy(director);
        entityManager.persist(price);
    }

    public void newPrice(Offering offering, FeeType type, long amount, LocalDate from) {
        transactionTemplate.executeWithoutResult(status -> addPrice(entityManager.getReference(Program.class, offering.programId()),
                entityManager.getReference(Person.class, offering.directorId()), type, offering.currency(), amount, from));
    }

    public Long newTemplate(Offering offering, int year) {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(year);
            term.setDescription(String.valueOf(year));
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(entityManager.getReference(Program.class, offering.programId()));
            template.setTerm(term);
            template.setRecurringFeeInvoicesCount(10);
            template.configureEnrollmentFees(2, 2, 8);
            template.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            entityManager.persist(template);
            return template.getId();
        });
    }

    public Long newCohort(Long templateId, CohortIntake intake, LocalDate cursadaStart, Percentage entryDiscount) {
        return transactionTemplate.execute(status -> {
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(entityManager.getReference(ProgramYearBillingTemplate.class, templateId));
            cohort.setIntake(intake);
            cohort.setAdmissionStart(cursadaStart.minusMonths(2));
            cohort.setAdmissionEnd(cursadaStart.minusDays(1));
            cohort.setCursadaStart(cursadaStart);
            cohort.setEntryFeeDiscount(entryDiscount);
            cohort.setCreatedBy(newPerson());
            entityManager.persist(cohort);
            return cohort.getId();
        });
    }

    public Long newStudent(Offering offering) {
        return transactionTemplate.execute(status -> {
            Student student = new Student();
            student.setPerson(newPerson());
            student.setInstitution(entityManager.getReference(Institution.class, offering.institutionId()));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student.getId();
        });
    }

    public Learner newLearner(Offering offering, Long cohortId) {
        return transactionTemplate.execute(status -> {
            Student student = new Student();
            student.setPerson(newPerson());
            student.setInstitution(entityManager.getReference(Institution.class, offering.institutionId()));
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = entityManager.getReference(Program.class, offering.programId());
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            if (cohortId != null) {
                enrollment.setProgramCohort(entityManager.getReference(ProgramCohort.class, cohortId));
            }
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
            entityManager.persist(enrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Plan");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            entityManager.persist(option);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(YearMonth.of(2027, 2));
            selection.setScopeMonths(10);
            selection.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            entityManager.persist(selection);
            return new Learner(student.getId(), enrollment.getId(), selection.getId());
        });
    }

    public Long grantScholarship(Offering offering, Learner learner, Percentage rate, int installments, ScholarshipScope scope) {
        return transactionTemplate.execute(status -> {
            Scholarship scholarship = new Scholarship();
            scholarship.setProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, learner.programEnrollmentId()));
            scholarship.setValue(new MonetaryAdjustment.Proportional(rate));
            scholarship.setReason("Beca " + rate.points().stripTrailingZeros().toPlainString() + "%");
            scholarship.setAppliesTo(scope);
            scholarship.setGrantedBy(entityManager.getReference(Person.class, offering.directorId()));
            scholarship.addInstallments(installments);
            entityManager.persist(scholarship);
            return scholarship.getId();
        });
    }

    public Long redeemPromotion(Offering offering, Learner learner, FeeType feeType, Percentage rate, Integer scopeCount) {
        return transactionTemplate.execute(status -> {
            Promotion promotion = new Promotion();
            promotion.setDescription("Promo " + rate.points().stripTrailingZeros().toPlainString() + "%");
            promotion.setProgram(entityManager.getReference(Program.class, offering.programId()));
            promotion.setEligibilityValidFrom(LocalDate.of(2020, 1, 1));
            promotion.setEligibilityValidTo(LocalDate.of(2030, 1, 1));
            promotion.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            promotion.setCreatedAt(LocalDateTime.of(2027, 1, 1, 0, 0));
            entityManager.persist(promotion);
            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setPromotion(promotion);
            benefit.setFeeType(feeType);
            benefit.setValue(new MonetaryAdjustment.Proportional(rate));
            benefit.setInvoiceScopeCount(scopeCount);
            entityManager.persist(benefit);
            PromotionRedemption redemption = new PromotionRedemption();
            redemption.setPromotion(promotion);
            redemption.setPaymentPlanSelection(entityManager.getReference(PaymentPlanSelection.class, learner.selectionId()));
            redemption.setRedeemedAt(LocalDateTime.of(2027, 2, 1, 0, 0));
            entityManager.persist(redemption);
            return benefit.getId();
        });
    }
}
