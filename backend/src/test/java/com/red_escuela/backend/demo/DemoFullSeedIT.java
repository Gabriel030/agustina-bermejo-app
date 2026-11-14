package com.red_escuela.backend.demo;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.DemoEducationalSeedRunner;
import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.DemoLearnerAccountsSeeder;
import com.red_escuela.backend.identity.specialist.LocalSeedRunner;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.testsupport.NightlyTest;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {"demo.dataset=full", "demo.seed.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NightlyTest(reason = "Seeds the full demo dataset (the default of the demo profile) through the real HTTP API; guards demo data only, not product behavior")
@SlowTest(reason = "Seeds the full demo academic base through the real HTTP API of a started application")
class DemoFullSeedIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
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
    private DemoSeedSession session;

    private DemoHttpClient api;

    @BeforeAll
    void seedFullDatasetOnce() {
        jdbcTemplate.queryForObject("select setval(pg_get_serial_sequence('student', 'id'), 5000)", Long.class);
        LocalSeedRunner localSeedRunner = new LocalSeedRunner(personRepository, roleRepository, personFactory, passwordEncoder);
        new DemoLearnerAccountsSeeder(localSeedRunner, personRepository, roleRepository, personFactory, passwordEncoder,
                institutionRepository, studentRepository, instructorProfileCreationPort).run(new DefaultApplicationArguments());
        new DemoEducationalSeedRunner(environment, jdbcTemplate).seedThroughPort(port);
        demoSeedRunner.seedThroughPort(port);
        api = new DemoHttpClient(port);
    }

    @Test
    void fullSeedCreatesEveryProgramAndCourseOfTheCatalog() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);

        List<String> programs = names(api.get(DemoPersona.ADMIN, "/programs"));
        List<String> courses = names(api.get(DemoPersona.ADMIN, "/courses"));

        dataset.programs().forEach(program -> assertThat(programs).contains(program.name()));
        dataset.courses().forEach(course -> assertThat(courses).contains(course.name()));
    }

    @Test
    void fullSeedCreatesEveryStudentWithAProgramEnrollment() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);

        for (int index = 0; index < dataset.studentCount(); index++) {
            String username = DemoPeopleSeedStep.studentUsername(index);
            assertThat(DemoPeopleSeedStep.findStudentId(session, username)).isNotNull();
            assertThat(api.get(username, "/me/program-enrollments")).isNotEmpty();
        }
    }

    private List<String> names(JsonNode items) {
        return items.valueStream().map(item -> item.path("name").asString("")).toList();
    }
}
