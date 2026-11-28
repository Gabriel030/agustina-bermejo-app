package com.red_escuela.backend.simulation;

import com.red_escuela.backend.academicstructure.service.HolidaysMissingForNextYearReminderJob;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestDatabaseIsolation;
import com.red_escuela.backend.demo.DemoClock;
import com.red_escuela.backend.demo.DemoDataset;
import com.red_escuela.backend.demo.DemoEnrollmentSeedStep;
import com.red_escuela.backend.demo.DemoSeedSession;
import com.red_escuela.backend.demo.DemoSeedStep;
import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentWaitlistExpiryJob;
import com.red_escuela.backend.enrollment.service.WaitlistExpiryJob;
import com.red_escuela.backend.evaluation.specialist.ClosureConfirmationReminderJob;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigReminderJob;
import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.DemoLearnerAccountsSeeder;
import com.red_escuela.backend.identity.specialist.LocalSeedRunner;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.specialist.InitialBillingModeProvisioner;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.testsupport.NightlyTest;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {
        "demo.dataset=full",
        "demo.seed.enabled=false",
        "app.scheduling.enabled=false",
        "platform.provisioning.initial-billing-mode=OFF"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NightlyTest(reason = "Simulates a full school year day by day through the real HTTP API; guards the simulation engine, not a product rule")
@SlowTest(reason = "Drives one simulated school year of 38 students through the real HTTP API of a started application")
class FirstYearSimulationIT extends AbstractIntegrationTest {

    private static final long SEED = 20260101L;
    private static final int YEAR = 2026;
    private static final LocalDate SEED_DAY = LocalDate.of(YEAR, 2, 20);
    private static final LocalDate FIRST_DAY = LocalDate.of(YEAR, 3, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(YEAR, 12, 20);

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PersonFactory personFactory;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private InstructorProfileCreationPort instructorProfileCreationPort;
    @Autowired
    private DemoSeedSession session;
    @Autowired
    private List<DemoSeedStep> steps;
    @Autowired
    private DemoClock demoClock;
    @Autowired
    private WaitlistExpiryJob waitlistExpiryJob;
    @Autowired
    private CourseEnrollmentWaitlistExpiryJob courseWaitlistExpiryJob;
    @Autowired
    private ClosureConfirmationReminderJob closureReminderJob;
    @Autowired
    private SectionEvaluationConfigReminderJob configReminderJob;
    @Autowired
    private HolidaysMissingForNextYearReminderJob holidaysReminderJob;
    @Autowired
    private InitialBillingModeProvisioner billingModeProvisioner;
    @Autowired
    private List<FeatureSnapshotHolder> featureSnapshotHolders;
    @Autowired
    private List<InstituteSettingsSnapshotHolder> settingsSnapshotHolders;

    @Test
    void oneSchoolYearOfThirtyEightStudentsRunsWithoutServerErrorsAndIsDeterministic() throws IOException {
        SimulationReport first = simulateYear();
        TestDatabaseIsolation.restoreNow();
        billingModeProvisioner.provisionInitialBillingMode(BillingMode.OFF);
        featureSnapshotHolders.forEach(FeatureSnapshotHolder::refresh);
        settingsSnapshotHolders.forEach(InstituteSettingsSnapshotHolder::refresh);
        SimulationReport second = simulateYear();

        writeReport(first);
        System.out.println(first.toJson());
        System.out.println("simulation: second run took " + second.elapsedMillis() + " ms");
        assertThat(first.counter("attendanceSheets")).isPositive();
        assertThat(first.counter("examPublications")).isPositive();
        assertThat(first.simulatedDays()).isEqualTo(LAST_DAY.toEpochDay() - FIRST_DAY.toEpochDay() + 1);
        assertThat(first.counter("dailyJobRuns")).isEqualTo(first.simulatedDays());
        assertThat(second.digest()).isEqualTo(first.digest());
    }

    private SimulationReport simulateYear() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);
        SimulationReport report = new SimulationReport(SEED, YEAR);
        SyntheticHolidayCalendar calendar = new SyntheticHolidayCalendar(SEED);
        demoClock.travelTo(SEED_DAY);
        ApplicationTime.useClock(demoClock);
        jdbcTemplate.queryForObject("select setval(pg_get_serial_sequence('student', 'id'), 5000)", Long.class);
        LocalSeedRunner localSeedRunner = new LocalSeedRunner(personRepository, roleRepository, personFactory, passwordEncoder);
        new DemoLearnerAccountsSeeder(localSeedRunner, personRepository, roleRepository, personFactory, passwordEncoder,
                institutionRepository, studentRepository, instructorProfileCreationPort).run(new DefaultApplicationArguments());
        session.open(port);
        DemoHttpClient api = session.api();
        SimulationClockDriver driver = new SimulationClockDriver(demoClock, waitlistExpiryJob, courseWaitlistExpiryJob,
                closureReminderJob, configReminderJob, holidaysReminderJob, report);
        YearSimulation simulation = new YearSimulation(api, jdbcTemplate, calendar, driver, report, SEED, YEAR);
        simulation.loadSyntheticHolidays(YEAR, YEAR + 2);
        steps.stream().sorted(Comparator.comparingInt(DemoSeedStep::order))
                .filter(step -> step.order() <= DemoEnrollmentSeedStep.STEP_ORDER)
                .forEach(step -> step.seed(dataset));
        simulation.run(session.enrolledSectionIdsOf(dataset), FIRST_DAY, LAST_DAY);
        return report;
    }

    private void writeReport(SimulationReport report) throws IOException {
        Path directory = Path.of("target", "simulation");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("year-" + YEAR + "-seed-" + SEED + ".json"), report.toJson());
    }
}
