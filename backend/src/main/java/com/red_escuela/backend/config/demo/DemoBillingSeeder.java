package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderPlanningJob;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.BillingOperations;
import com.red_escuela.backend.billing.payment.operation.RegisterCashPaymentCommand;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;
import com.red_escuela.backend.billing.service.MonthlyInstallmentIssuanceJob;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("local & demo")
@Order(200)
public class DemoBillingSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoBillingSeeder.class);
    private static final LocalDate EFFECTIVE_SINCE = LocalDate.of(2024, 1, 1);
    private static final YearMonth FIRST_MONTH = YearMonth.of(2026, 3);

    private enum Behavior {
        PAYS_ON_TIME, PAYS_ON_TIME_B, PAYS_ON_TIME_C, STOPS_PAYING_IN_JULY, SKIPS_LAST_MONTH, NEVER_PAYS
    }

    private final JdbcTemplate jdbc;
    private final DemoClock clock;
    private final PersonRepository personRepository;
    private final ProgramRepository programRepository;
    private final TermRepository termRepository;
    private final ConfigurationVersionRepository configurationVersionRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramCohortRepository cohortRepository;
    private final PaymentPlanOptionRepository planOptionRepository;
    private final PaymentPlanSelectionRepository planSelectionRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final MonthlyInstallmentIssuanceJob issuanceJob;
    private final InvoiceOverdueSweepService overdueSweep;
    private final BillingOperations operations;
    private final BillingOperationPipeline pipeline;
    private final TransactionTemplate transactions;
    private final CashPaymentPreviewService cashPreview;
    private final boolean skipBilling;
    private final DemoBillingScenarios scenarios;
    private final CollectionReminderPlanningJob reminderJob;

    public DemoBillingSeeder(JdbcTemplate jdbc, DemoClock clock, PersonRepository personRepository, ProgramRepository programRepository,
                             TermRepository termRepository, ConfigurationVersionRepository configurationVersionRepository,
                             ProgramYearBillingTemplateRepository templateRepository, ProgramCohortRepository cohortRepository,
                             PaymentPlanOptionRepository planOptionRepository, PaymentPlanSelectionRepository planSelectionRepository,
                             ProgramEnrollmentRepository programEnrollmentRepository, MonthlyInstallmentIssuanceJob issuanceJob,
                             InvoiceOverdueSweepService overdueSweep, BillingOperations operations, BillingOperationPipeline pipeline,
                             TransactionTemplate transactions, CashPaymentPreviewService cashPreview,
                             @Value("${demo.skip-billing:false}") boolean skipBilling, DemoBillingScenarios scenarios,
                             CollectionReminderPlanningJob reminderJob) {
        this.reminderJob = reminderJob;
        this.scenarios = scenarios;
        this.skipBilling = skipBilling;
        this.jdbc = jdbc;
        this.clock = clock;
        this.personRepository = personRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.configurationVersionRepository = configurationVersionRepository;
        this.templateRepository = templateRepository;
        this.cohortRepository = cohortRepository;
        this.planOptionRepository = planOptionRepository;
        this.planSelectionRepository = planSelectionRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.issuanceJob = issuanceJob;
        this.overdueSweep = overdueSweep;
        this.operations = operations;
        this.pipeline = pipeline;
        this.transactions = transactions;
        this.cashPreview = cashPreview;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (skipBilling) {
            log.info("Seed demo de facturacion omitido: demo.skip-billing=true");
            return;
        }
        Long institutions = jdbc.queryForObject("select count(*) from institution", Long.class);
        Long invoices = jdbc.queryForObject("select count(*) from invoice", Long.class);
        if (institutions == 0 || invoices > 0) {
            log.info("Seed demo de facturacion omitido: instituciones={}, facturas={}", institutions, invoices);
            return;
        }
        long startedAt = System.currentTimeMillis();
        LocalDate today = clock.getZone() == null ? LocalDate.now() : LocalDate.now(clock);
        Person director = personRepository.findByUsername("director.demo").orElseThrow();
        Person administrative = personRepository.findByUsername("administrativo.demo").orElseThrow();
        Map<Long, Behavior> behaviors;
        try {
            StatusChangeActor actor = new StatusChangeActor(administrative.getId(), null);
            clock.travelTo(LocalDate.of(2026, 1, 5));
            List<ProgramEnrollment> billed = transactions.execute(status -> {
                publishLatePaymentTerms(director);
                return configureBillingAndSelectPlans(director);
            });
            behaviors = assignBehaviors(billed);
            for (YearMonth month = FIRST_MONTH; !month.isAfter(YearMonth.from(today)); month = month.plusMonths(1)) {
                clock.travelTo(month.atDay(2));
                issuanceJob.run(month);
                clock.travelTo(month.atDay(8));
                payOnTime(month, behaviors, actor);
                clock.travelTo(month.atDay(Math.min(20, month.lengthOfMonth())));
                overdueSweep.run(LocalDate.now(clock));
                if (!month.isBefore(YearMonth.of(2026, 8))) {
                    int lastDay = month.equals(YearMonth.from(today)) ? Math.min(today.getDayOfMonth(), month.lengthOfMonth()) : month.lengthOfMonth();
                    for (int day = 3; day <= lastDay; day++) {
                        clock.travelTo(month.atDay(day));
                        reminderJob.run(month.atDay(day));
                    }
                }
            }
        } finally {
            clock.backToPresent();
        }
        overdueSweep.run(LocalDate.now(clock));
        reminderJob.run(LocalDate.now(clock));
        scenarios.seed(studentsOf(behaviors, Behavior.NEVER_PAYS), studentsOf(behaviors, Behavior.STOPS_PAYING_IN_JULY),
                studentsOf(behaviors, Behavior.PAYS_ON_TIME, Behavior.PAYS_ON_TIME_B, Behavior.PAYS_ON_TIME_C), director.getId(), administrative.getId());
        reminderJob.run(LocalDate.now(clock));
        log.info("Seed demo de facturacion listo en {} ms", System.currentTimeMillis() - startedAt);
    }

    private List<Long> studentsOf(Map<Long, Behavior> behaviors, Behavior... wanted) {
        List<Behavior> accepted = List.of(wanted);
        return behaviors.entrySet().stream().filter(e -> accepted.contains(e.getValue())).map(Map.Entry::getKey).toList();
    }

    private void publishLatePaymentTerms(Person director) {
        LatePaymentTerms terms = new LatePaymentTerms(5, Percentage.ofFraction(new BigDecimal("0.002")), 60, 3, true, 10, true, true);
        publish(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), terms, director, "Recargo por mora inicial de la institucion");
        publish(ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS), CollectionReminderSettings.initial(), director, "Avisos de cobranza iniciales");
        publish(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), new ProgramReminderSchedule(ReminderSchedule.initial()), director,
                "Cronograma de avisos para carreras");
        publish(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.MONTHLY_COURSES), new CourseReminderSchedule(ReminderSchedule.initial()), director,
                "Cronograma de avisos para cursos");
    }

    private void publish(ConfigurationTopic topic, ConfigurationValues values, Person director, String reason) {
        configurationVersionRepository.saveAndFlush(ConfigurationVersion.publish(topic, values, EFFECTIVE_SINCE, reason, director.getId(),
                LocalDate.now(clock), null, Instant.now(clock)));
    }

    private List<ProgramEnrollment> configureBillingAndSelectPlans(Person director) {
        Term term = termRepository.findAll().stream().filter(t -> t.getYear() == 2026).findFirst().orElseThrow();
        List<ProgramEnrollment> billed = new ArrayList<>();
        for (Program program : programRepository.findAll()) {
            if (DemoCatalog.PROGRAMS.stream().noneMatch(p -> p.name().equals(program.getName()))) {
                continue;
            }
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            template.setCreatedBy(director);
            final ProgramYearBillingTemplate savedTemplate = templateRepository.save(template);
            template = savedTemplate;
            publish(ConfigurationTopic.ofYearTerms(program.getId(), term.getId()), YearBillingTerms.initial(8), director,
                    "Calendario de cobro 2026: 8 cuotas mensuales");

            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.of(2026, 1, 5));
            cohort.setAdmissionEnd(LocalDate.of(2026, 3, 31));
            cohort.setCursadaStart(LocalDate.of(2026, 3, 2));
            cohort.setCreatedBy(director);
            cohort = cohortRepository.save(cohort);

            insertPrice(program.getId(), "MONTHLY_INSTALLMENT", "Cuota mensual", monthlyPriceOf(program), director);
            insertPrice(program.getId(), "ENROLLMENT_FEE", "Matricula anual", enrollmentFeeOf(program), director);

            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(program);
            option.setDescription("Cuota mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(director);
            option = planOptionRepository.save(option);

            for (ProgramEnrollment enrollment : programEnrollmentRepository.findAll()) {
                if (!enrollment.getProgram().getId().equals(program.getId()) || enrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED) {
                    continue;
                }
                if (enrollment.getEnrollmentDate().getYear() == 2026) {
                    enrollment.setProgramCohort(cohort);
                } else {
                    enrollment.setCurrentBillingTemplate(template);
                }
                programEnrollmentRepository.save(enrollment);
                PaymentPlanSelection selection = new PaymentPlanSelection();
                selection.setProgramEnrollment(enrollment);
                selection.setPaymentPlanOption(option);
                selection.setEffectiveFrom(YearMonth.of(2026, 1));
                selection.setScopeMonths(8);
                selection.setCreatedBy(director);
                planSelectionRepository.save(selection);
                billed.add(enrollment);
            }
        }
        return billed;
    }

    private BigDecimal monthlyPriceOf(Program program) {
        return DemoCatalog.PROGRAMS.stream().filter(p -> p.name().equals(program.getName())).findFirst().orElseThrow().monthlyPrice();
    }

    private BigDecimal enrollmentFeeOf(Program program) {
        return DemoCatalog.PROGRAMS.stream().filter(p -> p.name().equals(program.getName())).findFirst().orElseThrow().enrollmentFee();
    }

    private void insertPrice(Long programId, String feeType, String description, BigDecimal amount, Person director) {
        Long priceId = jdbc.queryForObject("insert into price (program_id, course_id, fee_type, currency, description) values (?, null, ?, 'ARS', ?) returning id",
                Long.class, programId, feeType, description);
        jdbc.update("insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, proposed_at, "
                        + "decided_by_id, decided_at, direct_publication, initial_version) values (?, ?, 'ARS', ?, 'EFFECTIVE', 'Precio inicial', ?, now(), ?, now(), true, true)",
                priceId, amount, EFFECTIVE_SINCE, director.getId(), director.getId());
    }

    private Map<Long, Behavior> assignBehaviors(List<ProgramEnrollment> billed) {
        Map<Long, Behavior> behaviors = new java.util.LinkedHashMap<>();
        Behavior[] values = Behavior.values();
        for (int i = 0; i < billed.size(); i++) {
            behaviors.put(billed.get(i).getStudent().getId(), values[i % values.length]);
        }
        return behaviors;
    }

    private void payOnTime(YearMonth month, Map<Long, Behavior> behaviors, StatusChangeActor actor) {
        for (Map.Entry<Long, Behavior> entry : behaviors.entrySet()) {
            Behavior behavior = entry.getValue();
            boolean pays = switch (behavior) {
                case PAYS_ON_TIME, PAYS_ON_TIME_B, PAYS_ON_TIME_C -> true;
                case STOPS_PAYING_IN_JULY -> month.isBefore(YearMonth.of(2026, 7));
                case SKIPS_LAST_MONTH -> month.isBefore(YearMonth.from(LocalDate.now()));
                case NEVER_PAYS -> false;
            };
            if (pays) {
                payOutstandingInCash(entry.getKey(), actor);
            }
        }
    }

    private void payOutstandingInCash(Long studentId, StatusChangeActor actor) {
        BigDecimal outstanding = jdbc.queryForObject("select coalesce(sum(i.outstanding_amount), 0) from invoice i "
                + "join student_current_account a on a.id = i.student_current_account_id where a.student_id = ? and i.outstanding_amount > 0",
                BigDecimal.class, studentId);
        if (outstanding.signum() <= 0) {
            return;
        }
        Money amount = Money.of(outstanding, Currency.ARS);
        transactions.executeWithoutResult(status -> {
            String fingerprint = cashPreview.previewCashPaymentOfStudent(studentId, amount).fingerprint();
            RegisterCashPaymentCommand command = new RegisterCashPaymentCommand(studentId, amount, LocalDate.now(clock),
                    "REC-" + studentId + "-" + LocalDate.now(clock), null, fingerprint, false, "demo-cash-" + studentId + "-" + LocalDate.now(clock), actor);
            execute(BillingOperationType.REGISTER_PAYMENT, command);
        });
    }

    private <C> BillingOperationOutcome<Object> execute(BillingOperationType type, C command) {
        BillingOperation<C, Object> operation = operations.operationOf(type);
        return pipeline.runOperation(operation, command);
    }
}
