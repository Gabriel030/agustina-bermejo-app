package com.red_escuela.backend.demo;

import com.red_escuela.backend.identity.api.CohortResponsibleAssigner;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {"demo.dataset=full", "demo.seed.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NightlyTest(reason = "Seeds the full demo dataset through the real HTTP API; guards demo data only, not product behavior")
@SlowTest(reason = "Seeds the full demo dataset through the real HTTP API of a started application")
abstract class AbstractFullDemoSeedIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected CohortResponsibleAssigner preceptorAssigner;
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

    protected DemoHttpClient api;
    protected long seedMillis;

    @BeforeAll
    void seedFullDatasetOnce() {
        jdbcTemplate.queryForObject("select setval(pg_get_serial_sequence('student', 'id'), 5000)", Long.class);
        LocalSeedRunner localSeedRunner = new LocalSeedRunner(personRepository, roleRepository, personFactory, passwordEncoder);
        new DemoLearnerAccountsSeeder(localSeedRunner, personRepository, roleRepository, personFactory, passwordEncoder,
                institutionRepository, studentRepository, instructorProfileCreationPort).run(new DefaultApplicationArguments());
        new DemoEducationalSeedRunner(environment, jdbcTemplate, preceptorAssigner).seedThroughPort(port);
        long startedAt = System.currentTimeMillis();
        demoSeedRunner.seedThroughPort(port);
        seedMillis = System.currentTimeMillis() - startedAt;
        System.out.println(getClass().getSimpleName() + ": seed full en " + seedMillis + " ms");
        api = new DemoHttpClient(port);
    }
}
