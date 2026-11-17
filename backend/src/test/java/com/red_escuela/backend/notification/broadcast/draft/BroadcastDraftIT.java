package com.red_escuela.backend.notification.broadcast.draft;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BroadcastDraftIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private static String body(String channel, String extra) {
        return "{\"name\":\"Aviso\",\"channel\":\"" + channel + "\",\"category\":\"INSTITUTIONAL\",\"segmentSpec\":{\"version\":1,"
                + "\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"DOCENTE\"}]}" + extra + "}";
    }

    @Test
    void createsDraftWithSnapshotsAndDoesNotFreezeRecipients() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-director", true, "DIRECTOR");
        AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-teacher", true, "DOCENTE");

        String response = mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"subject\":\"Hola\",\"bodyText\":\"Texto\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
        assertThat(jdbc.queryForObject("select subject_snapshot from broadcast where id = ?", String.class, id)).isEqualTo("Hola");
        assertThat(jdbc.queryForObject("select body_snapshot from broadcast where id = ?", String.class, id)).isEqualTo("Texto");
        assertThat(jdbc.queryForObject("select segment_spec ->> 'version' from broadcast where id = ?", String.class, id)).isEqualTo("1");
        assertThat(jdbc.queryForObject("select count(*) from broadcast_recipient where broadcast_id = ?", Long.class, id)).isZero();
    }

    @Test
    void copiesSubjectAndBodyFromTheTemplate() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-tpl-director", true, "DIRECTOR");
        Long templateId = jdbc.queryForObject("insert into message_template (name, category, channel, subject, body_text, created_by_person_id, created_at, updated_at) "
                + "values ('T', 'INSTITUTIONAL', 'EMAIL', 'Asunto plantilla', 'Cuerpo plantilla', ?, now(), now()) returning id", Long.class, director.getId());

        String response = mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"templateId\":" + templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
        assertThat(jdbc.queryForObject("select subject_snapshot from broadcast where id = ?", String.class, id)).isEqualTo("Asunto plantilla");
        assertThat(jdbc.queryForObject("select body_snapshot from broadcast where id = ?", String.class, id)).isEqualTo("Cuerpo plantilla");
    }

    @Test
    void emailDraftWithoutSubjectIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-nosubject", true, "DIRECTOR");

        mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"bodyText\":\"Texto\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentAndPreceptorGetForbidden() throws Exception {
        Person student = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-student", true, "ALUMNO");
        Person preceptor = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-preceptor", true, "PRECEPTOR");

        for (Person actor : new Person[]{student, preceptor}) {
            mockMvc.perform(post("/broadcasts").header("Authorization", bearer(actor))
                            .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"subject\":\"Hola\",\"bodyText\":\"Texto\"")))
                    .andExpect(status().isForbidden());
        }
    }
}
