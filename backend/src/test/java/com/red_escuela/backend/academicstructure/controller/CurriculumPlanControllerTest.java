package com.red_escuela.backend.academicstructure.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentRaceFixtures;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurriculumPlanControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ProgramSubjectService programSubjectService;

    private String administrativeToken() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername("admin-plan-" + System.nanoTime());
        person.setEmail(person.getUsername() + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private ProgramSubject entryOf(Program program) {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia " + subject.getCode());
        entityManager.persist(subject);
        entityManager.flush();
        return programSubjectService.addSubject(program.getId(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null));
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Tecnicatura HTTP " + System.nanoTime());
        program.setTotalYears(3);
        entityManager.persist(program);
        entityManager.flush();
        return program;
    }

    private long createDraftId(String token, Long programId, String body) throws Exception {
        String response = mockMvc.perform(post("/programs/{id}/curriculum-plans", programId)
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", token).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    @Test
    void editingCreditsOfAPlanWithEnrolledStudentsReturns409WithTheLockedCode() throws Exception {
        String token = administrativeToken();
        Program program = new ProgramEnrollmentRaceFixtures(entityManager).cycle().program();
        ProgramSubject entry = entryOf(program);
        new ProgramEnrollmentRaceFixtures(entityManager).programEnrollment(program);
        entityManager.flush();

        mockMvc.perform(put("/program-subjects/{id}", entry.getId())
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", token).content("{\"credits\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURRICULUM_PLAN_LOCKED"));
    }

    @Test
    void activatingAnAlreadyActivePlanReturns409WithTheTransitionCode() throws Exception {
        String token = administrativeToken();
        Program program = persistProgram();
        Long planId = entryOf(program).getPlan().getId();

        mockMvc.perform(post("/curriculum-plans/{id}/activate", planId).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CURRICULUM_PLAN_TRANSITION"));
    }

    @Test
    void activatingADraftWithoutSubjectsReturns409WithTheWithoutSubjectsCode() throws Exception {
        String token = administrativeToken();
        Program program = persistProgram();
        entryOf(program);
        long draftId = createDraftId(token, program.getId(), "{\"name\":\"Vacío\",\"validFrom\":\"2028-03-01\"}");

        mockMvc.perform(post("/curriculum-plans/{id}/activate", draftId).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURRICULUM_PLAN_WITHOUT_SUBJECTS"));
    }

    @Test
    void expiringWithAGraduationDeadlineBeforeTheExpirationReturns400WithTheDatesCode() throws Exception {
        String token = administrativeToken();
        Program program = persistProgram();
        Long planId = entryOf(program).getPlan().getId();

        mockMvc.perform(post("/curriculum-plans/{id}/expire", planId)
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", token)
                        .content("{\"expiresOn\":\"2030-12-31\",\"graduationDeadline\":\"2030-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRICULUM_PLAN_DATES"));
    }

    @Test
    void draftPlanOfAProgramWithStudentsStaysEditableOverHttp() throws Exception {
        String token = administrativeToken();
        Program program = new ProgramEnrollmentRaceFixtures(entityManager).cycle().program();
        entryOf(program);
        new ProgramEnrollmentRaceFixtures(entityManager).programEnrollment(program);
        long draftId = createDraftId(token, program.getId(), "{\"name\":\"Borrador\",\"validFrom\":\"2028-03-01\"}");

        mockMvc.perform(put("/curriculum-plans/{id}", draftId)
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", token).content("{\"totalCreditsRequired\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreditsRequired").value(50));
        assertThat(draftId).isPositive();
    }
}
