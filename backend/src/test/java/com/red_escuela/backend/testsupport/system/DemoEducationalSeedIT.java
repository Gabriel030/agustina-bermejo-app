package com.red_escuela.backend.testsupport.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.infrastructure.DemoApiClient;
import com.red_escuela.backend.config.DemoEducationalSeedRunner;
import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.DemoLearnerAccountsSeeder;
import com.red_escuela.backend.identity.service.LocalSeedRunner;
import com.red_escuela.backend.identity.service.PersonFactory;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SlowTest(reason = "Seeds the demo educational data through the real HTTP API of a started application")
class DemoEducationalSeedIT extends AbstractIntegrationTest {
    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
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

    private DemoApiClient api;

    private void seed() {
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();
        LocalSeedRunner localSeedRunner = new LocalSeedRunner(personRepository, roleRepository, personFactory, passwordEncoder);
        new DemoLearnerAccountsSeeder(localSeedRunner, personRepository, roleRepository, personFactory, passwordEncoder,
                institutionRepository, studentRepository, instructorProfileCreationPort).run(arguments);
        new DemoEducationalSeedRunner(environment).seedThroughPort(port);
        api = new DemoApiClient(port);
    }

    private long countByName(JsonNode items, String name) {
        long matches = 0;
        for (JsonNode item : items) {
            if (name.equals(item.path("name").asText())) {
                matches++;
            }
        }
        return matches;
    }

    @Test
    void seedCreatesProgramTermAndEnrollmentsForEachDemoLearner() {
        seed();

        assertThat(countByName(api.get("administrativo.demo", "/programs"), DemoEducationalSeedRunner.PROGRAM_NAME)).isEqualTo(1);
        assertThat(api.get("alumno.demo", "/me/program-enrollments")).hasSize(1);
        assertThat(api.get("alumno.demo", "/me/section-enrollments")).hasSize(2);
        assertThat(api.get("alumno.egreso.demo", "/me/program-enrollments")).hasSize(1);
        assertThat(api.get("alumno.cursos.demo", "/me/course-enrollments")).hasSize(1);
    }

    @Test
    void seedLeavesAJustifiedAbsenceWaitingForApproval() {
        seed();

        assertThat(api.get("administrativo.demo", "/attendance/justifications")).isNotEmpty();
    }

    @Test
    void runningTheSeedTwiceDoesNotDuplicateEducationalData() {
        seed();
        seed();

        assertThat(countByName(api.get("administrativo.demo", "/programs"), DemoEducationalSeedRunner.PROGRAM_NAME)).isEqualTo(1);
        assertThat(api.get("alumno.demo", "/me/section-enrollments")).hasSize(2);
    }
}
