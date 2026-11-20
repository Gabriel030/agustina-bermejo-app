package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AudiencePreviewMissingVariableIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private Person director;
    private Person withoutFirstName;
    private Person complete;

    @BeforeEach
    void seedPeople() {
        director = AudienceTestPersons.create(entityManager, roleRepository, "mv-director", true, null, "DIRECTOR");
        withoutFirstName = AudienceTestPersons.create(entityManager, roleRepository, "mv-blank-name", true, null, "PRECEPTOR");
        withoutFirstName.setName("");
        complete = AudienceTestPersons.create(entityManager, roleRepository, "mv-complete", true, null, "PRECEPTOR");
        entityManager.flush();
    }

    private ResultActions preview(String content, boolean excludeMissing) throws Exception {
        return mockMvc.perform(post("/broadcasts/audience-preview")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(director))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\",\"category\":\"INSTITUTIONAL\",\"excludeMissingVariables\":" + excludeMissing
                        + "," + content + ",\"segmentSpec\":{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":["
                        + "{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]},\"page\":0,\"size\":100}"));
    }

    @Test
    void blankFirstNameIsExcludedWhenExclusionIsRequested() throws Exception {
        preview("\"subject\":\"Hola\",\"bodyText\":\"Hola {{nombre}}\"", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.MISSING_TEMPLATE_VARIABLE").value(1))
                .andExpect(jsonPath("$.sample[?(@.personId==" + withoutFirstName.getId() + ")].status").value("EXCLUDED"))
                .andExpect(jsonPath("$.sample[?(@.personId==" + withoutFirstName.getId() + ")].exclusionReason")
                        .value("MISSING_TEMPLATE_VARIABLE"))
                .andExpect(jsonPath("$.sample[?(@.personId==" + complete.getId() + ")].status").value("INCLUDED"));
    }

    @Test
    void blankFirstNameIsReportedButKeptWhenExclusionIsNotRequested() throws Exception {
        preview("\"subject\":\"Hola\",\"bodyText\":\"Hola {{nombre}}\"", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.MISSING_TEMPLATE_VARIABLE").value(1))
                .andExpect(jsonPath("$.included").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.sample[?(@.personId==" + withoutFirstName.getId() + ")].status").value("INCLUDED"));
    }

    @Test
    void messageWithoutVariablesReportsNoMissingVariables() throws Exception {
        preview("\"subject\":\"Hola\",\"bodyText\":\"Texto fijo\"", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.MISSING_TEMPLATE_VARIABLE").value(0))
                .andExpect(jsonPath("$.included").value(2));
    }

    @Test
    void variableWithoutPersonSourceExcludesEveryone() throws Exception {
        preview("\"subject\":\"Hola\",\"bodyText\":\"Tu carrera: {{carrera}}\"", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.MISSING_TEMPLATE_VARIABLE").value(2))
                .andExpect(jsonPath("$.included").value(0));
    }

    @Test
    void templateIdSuppliesTheContentWhenNoBodyIsSent() throws Exception {
        MessageTemplate template = MessageTemplate.builder().name("mv-template").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).subject("Hola").bodyText("Hola {{nombre}}").createdByPersonId(director.getId())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        entityManager.persist(template);
        entityManager.flush();

        preview("\"templateId\":" + template.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.MISSING_TEMPLATE_VARIABLE").value(1));
    }

    @Test
    void unknownTemplateIdIsRejectedAsNotFound() throws Exception {
        preview("\"templateId\":999999999", true).andExpect(status().isNotFound());
    }

    @Test
    void unknownVariableInTheMessageIsRejected() throws Exception {
        preview("\"subject\":\"Hola\",\"bodyText\":\"Hola {{inexistente}}\"", true).andExpect(status().is4xxClientError());
    }
}
