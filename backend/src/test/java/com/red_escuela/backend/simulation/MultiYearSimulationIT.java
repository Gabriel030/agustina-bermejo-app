package com.red_escuela.backend.simulation;

import com.red_escuela.backend.academicstructure.service.HolidaysMissingForNextYearReminderJob;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestDatabaseIsolation;
import com.red_escuela.backend.demo.DemoAcademicOfferSeedStep;
import com.red_escuela.backend.demo.DemoCatalog;
import com.red_escuela.backend.demo.DemoClock;
import com.red_escuela.backend.demo.DemoPeopleSeedStep;
import com.red_escuela.backend.demo.DemoPersona;
import tools.jackson.databind.JsonNode;
import com.red_escuela.backend.demo.DemoDataset;
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
import com.red_escuela.backend.platform.specialist.InitialBillingModeProvisioner;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {
        "demo.dataset=ci",
        "demo.seed.enabled=false",
        "app.scheduling.enabled=false",
        "platform.provisioning.initial-billing-mode=OFF"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NightlyTest(reason = "Simulates five school years of seven student profiles through the real HTTP API; guards the simulation engine, not a product rule")
@SlowTest(reason = "Drives five simulated school years of eleven students through the real HTTP API of a started application")
class MultiYearSimulationIT extends AbstractIntegrationTest {

    private static final long SEED = 20260101L;
    private static final int YEAR = MultiYearSimulation.FIRST_YEAR;
    private static final LocalDate SEED_DAY = LocalDate.of(YEAR, 2, 20);
    private static final LocalDate FIRST_DAY = LocalDate.of(YEAR, 2, 21);
    private static final LocalDate LAST_DAY = LocalDate.of(MultiYearSimulation.LAST_YEAR, 12, 31);

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
    void fiveYearsOfSevenProfilesRunWithoutErrorsMeetExpectedOutcomesAndAreDeterministic() throws IOException {
        SimulationReport first = simulate();
        assertThat(failuresOfLastRun).isEmpty();
        assertThat(completedOfLastRun).isPositive();
        assertThat(first.counter("graduationsApproved")).isPositive();
        assertThat(first.counter("recoveries")).isPositive();
        TestDatabaseIsolation.restoreNow();
        billingModeProvisioner.provisionInitialBillingMode(BillingMode.OFF);
        featureSnapshotHolders.forEach(FeatureSnapshotHolder::refresh);
        settingsSnapshotHolders.forEach(InstituteSettingsSnapshotHolder::refresh);
        SimulationReport second = simulate();

        writeReport(first);
        System.out.println(first.toJson());
        System.out.println("simulation: second run took " + second.elapsedMillis() + " ms");
        assertThat(failuresOfLastRun).isEmpty();
        assertThat(second.digest()).isEqualTo(first.digest());
    }

    private List<String> failuresOfLastRun = List.of();
    private long completedOfLastRun;

    private static final List<StudentProfile> PROFILES = List.of(StudentProfile.GRADUATES, StudentProfile.FAILS_AND_RETAKES,
            StudentProfile.BECOMES_FREE, StudentProfile.RECOVERY_AND_FINAL, StudentProfile.DROPS_OUT, StudentProfile.REPEATS_YEAR,
            StudentProfile.PASSES_EVERYTHING, StudentProfile.PASSES_EVERYTHING, StudentProfile.PASSES_EVERYTHING,
            StudentProfile.PASSES_EVERYTHING, StudentProfile.PASSES_EVERYTHING);
    private static final int[] COHORT_YEARS = {2026, 2026, 2026, 2026, 2026, 2026, 2027, 2027, 2028, 2029, 2030};

    private SimulationReport simulate() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.CI);
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
        Set<LocalDate> holidays = MultiYearSimulation.loadHolidays(api, calendar, YEAR, MultiYearSimulation.LAST_YEAR + 1);
        steps.stream().sorted(Comparator.comparingInt(DemoSeedStep::order))
                .filter(step -> step.order() <= DemoAcademicOfferSeedStep.STEP_ORDER)
                .forEach(step -> step.seed(dataset));
        DemoCatalog.ProgramSpec program = dataset.programs().get(0);
        long programId = session.programIdsByName().get(program.name());
        long instructorId = leadInstructorId(api);
        MultiYearSimulation simulation = new MultiYearSimulation(api, jdbcTemplate, driver, report, SEED, program, programId, instructorId, holidays);
        for (int index = 0; index < PROFILES.size(); index++) {
            long studentId = DemoPeopleSeedStep.findStudentId(session, DemoPeopleSeedStep.studentUsername(index));
            simulation.registerPupil(index, PROFILES.get(index), COHORT_YEARS[index], studentId);
        }
        simulation.adoptSeededYear(YEAR);
        simulation.run(FIRST_DAY, LAST_DAY);
        failuresOfLastRun = simulation.expectationFailures();
        completedOfLastRun = simulation.completedWithGraduationRequestCount();
        return report;
    }

    private static long leadInstructorId(DemoHttpClient api) {
        for (JsonNode instructor : api.get(DemoPersona.ADMIN, "/instructors")) {
            if (DemoPersona.TEACHER_FULL_NAME.equals(instructor.path("name").asString(""))) {
                return instructor.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el perfil de docente");
    }

    private void writeReport(SimulationReport report) throws IOException {
        Path directory = Path.of("target", "simulation");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("multi-year-seed-" + SEED + ".json"), report.toJson());
    }
}
