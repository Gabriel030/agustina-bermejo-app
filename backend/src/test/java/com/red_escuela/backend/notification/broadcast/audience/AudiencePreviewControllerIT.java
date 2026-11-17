package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AudiencePreviewControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private static String previewBody(String role, String channel, int page, int size) {
        return "{\"channel\":\"" + channel + "\",\"category\":\"INSTITUTIONAL\",\"segmentSpec\":{\"version\":1,\"quantifier\":\"ALL\","
                + "\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"" + role + "\"}]},\"page\":" + page + ",\"size\":" + size + "}";
    }

    @Test
    void catalogListsAvailableSegmentsForDirector() throws Exception {
        Person director = AudienceTestPersons.create(entityManager, roleRepository, "aud-catalog", true, null, "DIRECTOR");

        mockMvc.perform(get("/broadcasts/segment-catalog").header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='STAFF_ROLE')].parameters[0]").value("role"))
                .andExpect(jsonPath("$[?(@.key=='PROGRAM')]").isNotEmpty());
    }

    @Test
    void previewCountsIncludedAndExcludedByReasonAndPaginatesTheSample() throws Exception {
        Person director = AudienceTestPersons.create(entityManager, roleRepository, "aud-director", true, null, "DIRECTOR");
        AudienceTestPersons.create(entityManager, roleRepository, "aud-ok", true, null, "PRECEPTOR");
        AudienceTestPersons.create(entityManager, roleRepository, "aud-unverified", false, null, "PRECEPTOR");
        AudienceTestPersons.create(entityManager, roleRepository, "aud-unverified-2", false, null, "PRECEPTOR");

        mockMvc.perform(post("/broadcasts/audience-preview").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(previewBody("PRECEPTOR", "EMAIL", 0, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excludedByReason.NO_VERIFIED_EMAIL").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.included").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.sample.length()").value(2))
                .andExpect(jsonPath("$.sample[0].addressMasked").value(containsString("***@")));
    }

    @Test
    void previewRejectsAnUnknownRoleValue() throws Exception {
        Person director = AudienceTestPersons.create(entityManager, roleRepository, "aud-invalid", true, null, "DIRECTOR");

        mockMvc.perform(post("/broadcasts/audience-preview").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(previewBody("ALUMNO", "EMAIL", 0, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEGMENT_CRITERION"));
    }

    @Test
    void studentAndPreceptorGetForbidden() throws Exception {
        Person student = AudienceTestPersons.create(entityManager, roleRepository, "aud-student", true, null, "ALUMNO");
        Person preceptor = AudienceTestPersons.create(entityManager, roleRepository, "aud-preceptor", true, null, "PRECEPTOR");

        for (Person actor : new Person[]{student, preceptor}) {
            mockMvc.perform(get("/broadcasts/segment-catalog").header("Authorization", bearer(actor))).andExpect(status().isForbidden());
            mockMvc.perform(post("/broadcasts/audience-preview").header("Authorization", bearer(actor))
                    .contentType(MediaType.APPLICATION_JSON).content(previewBody("DOCENTE", "EMAIL", 0, 10))).andExpect(status().isForbidden());
        }
    }
}
