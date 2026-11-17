package com.red_escuela.backend.notification.broadcast.template;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageTemplateControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private Person createPerson(String username, String role) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setName("Nora");
        person.setLastname("Gómez");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(role).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private static String body(String name, String category, String channel, String subject, String bodyText) {
        String subjectJson = subject == null ? "null" : "\"" + subject + "\"";
        return "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"channel\":\"" + channel
                + "\",\"subject\":" + subjectJson + ",\"bodyText\":\"" + bodyText + "\"}";
    }

    private long createTemplate(Person actor, String name, String channel, String subject) throws Exception {
        String response = mockMvc.perform(post("/message-templates").header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "INSTITUTIONAL", channel, subject, "Hola {{nombre}}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(response).read("$.id", Long.class);
    }

    @Test
    void directorCreatesListsUpdatesAndDeactivatesATemplate() throws Exception {
        Person director = createPerson("tpl-director", "DIRECTOR");
        long id = createTemplate(director, "Bienvenida", "EMAIL", "Hola");

        mockMvc.perform(get("/message-templates").header("Authorization", bearer(director)).param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].name").value("Bienvenida"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].active").value(true));
        mockMvc.perform(get("/message-templates").header("Authorization", bearer(director)).param("channel", "WHATSAPP_LINK"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());

        mockMvc.perform(put("/message-templates/" + id).header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renombrada", "PROMOTIONAL", "EMAIL", "Nuevo", "Chau {{apellido}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renombrada"))
                .andExpect(jsonPath("$.category").value("PROMOTIONAL"));

        mockMvc.perform(delete("/message-templates/" + id).header("Authorization", bearer(director)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/message-templates").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
        assertThat(jdbc.queryForObject("select active from message_template where id = ?", Boolean.class, id)).isFalse();
        mockMvc.perform(delete("/message-templates/" + id).header("Authorization", bearer(director)))
                .andExpect(status().isNotFound());
    }

    @Test
    void administrativeCanManageTemplates() throws Exception {
        Person administrative = createPerson("tpl-admin", "ADMINISTRATIVO");

        createTemplate(administrative, "Recordatorio", "WHATSAPP_LINK", null);
    }

    @Test
    void studentAndPreceptorGetForbiddenOnEveryEndpoint() throws Exception {
        Person student = createPerson("tpl-student", "ALUMNO");
        Person preceptor = createPerson("tpl-preceptor", "PRECEPTOR");

        for (Person actor : new Person[]{student, preceptor}) {
            String token = bearer(actor);
            mockMvc.perform(get("/message-templates").header("Authorization", token)).andExpect(status().isForbidden());
            mockMvc.perform(post("/message-templates").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(body("x", "INSTITUTIONAL", "EMAIL", "s", "b"))).andExpect(status().isForbidden());
            mockMvc.perform(put("/message-templates/1").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(body("x", "INSTITUTIONAL", "EMAIL", "s", "b"))).andExpect(status().isForbidden());
            mockMvc.perform(delete("/message-templates/1").header("Authorization", token)).andExpect(status().isForbidden());
            mockMvc.perform(post("/message-templates/render-preview").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"channel\":\"EMAIL\",\"subject\":\"s\",\"bodyText\":\"b\",\"personId\":1}")).andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsEmailTemplateWithoutSubjectAndUnknownVariables() throws Exception {
        Person director = createPerson("tpl-validation", "DIRECTOR");

        mockMvc.perform(post("/message-templates").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "INSTITUTIONAL", "EMAIL", null, "b")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_SUBJECT_REQUIRED"));
        mockMvc.perform(post("/message-templates").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "INSTITUTIONAL", "EMAIL", "s", "{{telefono}}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEMPLATE_VARIABLE"));
    }

    @Test
    void renderPreviewReplacesVariablesForThePerson() throws Exception {
        Person director = createPerson("tpl-preview", "DIRECTOR");
        Person target = createPerson("tpl-preview-target", "ALUMNO");

        mockMvc.perform(post("/message-templates/render-preview").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP_LINK\",\"subject\":null,\"bodyText\":\"Hola {{nombre}} {{apellido}}\",\"personId\":" + target.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Hola Nora Gómez"));
    }

    @Test
    void renderPreviewReportsUnknownAndMissingVariables() throws Exception {
        Person director = createPerson("tpl-preview-errors", "DIRECTOR");

        mockMvc.perform(post("/message-templates/render-preview").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"subject\":\"s\",\"bodyText\":\"{{foo}}\",\"personId\":" + director.getId() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEMPLATE_VARIABLE"))
                .andExpect(jsonPath("$.details.variable").value("foo"));
        mockMvc.perform(post("/message-templates/render-preview").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"subject\":\"s\",\"bodyText\":\"{{materia}}\",\"personId\":" + director.getId() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_VARIABLE"))
                .andExpect(jsonPath("$.details.variable").value("materia"));
    }
}
