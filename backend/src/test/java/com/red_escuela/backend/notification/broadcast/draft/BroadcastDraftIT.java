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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_SUBJECT_REQUIRED"));
    }

    @Test
    void whatsappDraftWithoutSubjectIsCreated() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-wa", true, "DIRECTOR");

        mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("WHATSAPP_LINK", ",\"bodyText\":\"Texto\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftWithoutTemplateAndWithoutBodyIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-nobody", true, "DIRECTOR");

        mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"subject\":\"Hola\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BROADCAST_BODY_REQUIRED"));
    }

    @Test
    void unknownOrInactiveTemplateIsNotFound() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-notpl", true, "DIRECTOR");
        Long inactiveId = insertTemplate(director, "INSTITUTIONAL", "EMAIL", false);

        for (Long templateId : new Long[]{inactiveId, inactiveId + 1000}) {
            mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                            .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"templateId\":" + templateId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MESSAGE_TEMPLATE_NOT_FOUND"));
        }
    }

    @Test
    void templateWithDifferentChannelIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-tpl-channel", true, "DIRECTOR");
        Long templateId = insertTemplate(director, "INSTITUTIONAL", "WHATSAPP_LINK", true);

        mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"templateId\":" + templateId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BROADCAST_TEMPLATE_MISMATCH"));
    }

    @Test
    void templateWithDifferentCategoryIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-tpl-category", true, "DIRECTOR");
        Long templateId = insertTemplate(director, "PROMOTIONAL", "EMAIL", true);

        mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"templateId\":" + templateId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BROADCAST_TEMPLATE_MISMATCH"));
    }

    @Test
    void subjectOrBodySentWithTemplateIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-tpl-content", true, "DIRECTOR");
        Long templateId = insertTemplate(director, "INSTITUTIONAL", "EMAIL", true);

        for (String extra : new String[]{",\"subject\":\"Otro\"", ",\"bodyText\":\"Otro\""}) {
            mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                            .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", ",\"templateId\":" + templateId + extra)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BROADCAST_CONTENT_WITH_TEMPLATE"));
        }
    }

    @Test
    void blankOrTooLongNameIsRejected() throws Exception {
        Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-name", true, "DIRECTOR");

        for (String name : new String[]{"  ", "a".repeat(161)}) {
            mockMvc.perform(post("/broadcasts").header("Authorization", bearer(director))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("EMAIL", ",\"subject\":\"Hola\",\"bodyText\":\"Texto\"").replace("\"name\":\"Aviso\"", "\"name\":\"" + name + "\"")))
                    .andExpect(status().isBadRequest());
        }
    }

    private Long insertTemplate(Person creator, String category, String channel, boolean active) {
        return jdbc.queryForObject("insert into message_template (name, category, channel, subject, body_text, active, created_by_person_id, created_at, updated_at) "
                + "values ('T', ?, ?, 'Asunto', 'Cuerpo', ?, ?, now(), now()) returning id", Long.class, category, channel, active, creator.getId());
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
