package com.red_escuela.backend.demo;

import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.DemoEducationalSeedRunner;
import com.red_escuela.backend.config.infrastructure.DemoApiClient;
import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.DemoLearnerAccountsSeeder;
import com.red_escuela.backend.identity.specialist.LocalSeedRunner;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {"demo.dataset=ci", "demo.seed.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SlowTest(reason = "Seeds the demo academic base through the real HTTP API of a started application")
class DemoSeedIT extends AbstractIntegrationTest {
    private static final List<String> DEMO_USERS = List.of("alumno.demo", "docente.demo", "preceptor.demo", "administrativo.demo", "director.demo");

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
    @Autowired
    private ApplicationContext context;
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
    private DemoSeedRunner demoSeedRunner;
    @Autowired
    private DemoClock demoClock;

    @Autowired
    private DemoSeedSession session;

    private DemoHttpClient api;
    private long seedMillis;

    @BeforeAll
    void seedBaseOnce() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 3, 18));
        LocalSeedRunner localSeedRunner = new LocalSeedRunner(personRepository, roleRepository, personFactory, passwordEncoder);
        new DemoLearnerAccountsSeeder(localSeedRunner, personRepository, roleRepository, personFactory, passwordEncoder,
                institutionRepository, studentRepository, instructorProfileCreationPort).run(new DefaultApplicationArguments());
        demoClock.travelTo(LocalDate.of(2026, 3, 18));
        new DemoEducationalSeedRunner(environment, jdbcTemplate).seedThroughPort(port);
        demoClock.backToPresent();
        long startedAt = System.currentTimeMillis();
        demoSeedRunner.seedThroughPort(port);
        seedMillis = System.currentTimeMillis() - startedAt;
        System.out.println("DemoSeedIT: seed ci en " + seedMillis + " ms");
        api = new DemoHttpClient(port);
    }

    @Test
    void seedCreatesInstitutionWithPlannedProgramsAndCourses() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.CI);

        JsonNode programs = api.get(DemoPersona.ADMIN, "/programs");
        JsonNode courses = api.get(DemoPersona.ADMIN, "/courses");

        dataset.programs().forEach(program -> assertThat(names(programs)).contains(program.name()));
        dataset.courses().forEach(course -> assertThat(names(courses)).contains(course.name()));
        assertThat(institutionRepository.count()).isEqualTo(1);
        for (int index = 0; index < dataset.studentCount(); index++) {
            String username = DemoPeopleSeedStep.studentUsername(index);
            assertThat(DemoPeopleSeedStep.findStudentId(session, username)).isNotNull();
            assertThat(api.get(username, "/me/program-enrollments")).hasSize(1);
        }
    }

    @Test
    void seedCreatesCorrelativesBetweenSubjectsOfTheSameProgram() {
        long programId = programId(DemoCatalog.EARLY_CHILDHOOD);
        int prerequisites = 0;
        for (JsonNode programSubject : api.get(DemoPersona.ADMIN, "/programs/" + programId + "/subjects")) {
            prerequisites += api.get(DemoPersona.ADMIN, "/program-subjects/" + programSubject.get("id").asLong() + "/prerequisites").size();
        }

        assertThat(prerequisites).isPositive();
    }

    @Test
    void seedCreatesAllFiveDemoUsersWithKnownPassword() {
        for (String username : DEMO_USERS) {
            assertThat(api.get(username, "/me").path("roles")).isNotEmpty();
        }
    }

    @Test
    void seedRunsOnlyOnceWhenMarkerAlreadyExists() {
        int programsBefore = api.get(DemoPersona.ADMIN, "/programs").size();
        Integer subjectsBefore = jdbcTemplate.queryForObject("select count(*) from subject", Integer.class);

        demoSeedRunner.seedThroughPort(port);

        assertThat(api.get(DemoPersona.ADMIN, "/programs")).hasSize(programsBefore);
        assertThat(jdbcTemplate.queryForObject("select count(*) from subject", Integer.class)).isEqualTo(subjectsBefore);
    }

    @Test
    void demoUsersAreNotDuplicatedWithLocalSeedRunner() {
        for (String username : DEMO_USERS) {
            assertThat(jdbcTemplate.queryForObject("select count(*) from person where username = ?", Integer.class, username)).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForList("select username from person group by username having count(*) > 1")).isEmpty();
    }

    @Test
    void richSeedCoexistsWithEducationalSeedRunner() {
        DemoApiClient educationalApi = new DemoApiClient(port);

        assertThat(names(educationalApi.get(DemoPersona.ADMIN, "/programs")))
                .contains(DemoEducationalSeedRunner.PROGRAM_NAME, DemoCatalog.EARLY_CHILDHOOD);
        assertThat(educationalApi.get("alumno.demo", "/me/program-enrollments")).hasSize(1);
        long activeAnnualTerms2026 = api.get(DemoPersona.ADMIN, "/terms").valueStream()
                .filter(term -> term.path("year").asInt() == 2026 && "ANNUAL".equals(term.path("type").asString(""))
                        && "ACTIVE".equals(term.path("status").asString("")))
                .count();
        assertThat(activeAnnualTerms2026).isEqualTo(1);
    }

    @Test
    void demoClockIsLoadedWithDemoProfile() {
        assertThat(context.getBeansOfType(DemoClock.class)).hasSize(1);
        assertThat(context.getBean(Clock.class)).isInstanceOf(DemoClock.class);
    }

    private long programId(String name) {
        for (JsonNode program : api.get(DemoPersona.ADMIN, "/programs")) {
            if (name.equals(program.path("name").asString(""))) {
                return program.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe la carrera " + name);
    }

    private List<String> names(JsonNode items) {
        return items.valueStream().map(item -> item.path("name").asString("")).toList();
    }
}
