package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.domain.TermType;
import java.math.BigDecimal;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademicStructureControllerTest extends AbstractIntegrationTest {
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

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();

        return jwtService.generateAccessToken(person);
    }

    @Test
    void creatingSubjectWithAdministrativeRoleReturns201() throws Exception {
        String token = tokenWithRole("admin-academico1", "ADMINISTRATIVO");
        CreateSubjectRequest request = new CreateSubjectRequest("Química General", "QUIM", null, null, null, null, null);

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Química General"));
    }

    @Test
    void creatingSubjectWithoutAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("preceptor-academico1", "PRECEPTOR");
        CreateSubjectRequest request = new CreateSubjectRequest("Biología", "BIO", null, null, null, null, null);

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingSubjectWithDuplicateNameReturns409() throws Exception {
        String token = tokenWithRole("admin-academico2", "ADMINISTRATIVO");
        CreateSubjectRequest request = new CreateSubjectRequest("Historia", "HIST", null, null, null, null, null);

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingProgramWithCreditsPersistsAllFields() throws Exception {
        String token = tokenWithRole("admin-academico3", "ADMINISTRATIVO");
        CreateProgramRequest request = new CreateProgramRequest(
                "Tecnicatura en Sistemas", 3, null, null, 240, null, null, null, null, null);

        mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tecnicatura en Sistemas"))
                .andExpect(jsonPath("$.totalCreditsRequired").value(240));
    }

    @Test
    void listingProgramsWithAnyAuthenticatedRoleReturns200() throws Exception {
        String token = tokenWithRole("alumno-academico1", "ALUMNO");

        mockMvc.perform(get("/programs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void creatingElectiveGroupAndAssigningRangeToProgram() throws Exception {
        String token = tokenWithRole("admin-academico4", "ADMINISTRATIVO");
        CreateElectiveGroupRequest groupRequest = new CreateElectiveGroupRequest("Idiomas", "Grupo de idiomas");

        String groupBody = mockMvc.perform(post("/elective-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupBody).get("id").asLong();

        CreateProgramRequest programRequest = new CreateProgramRequest(
                "Tecnicatura con Electivas", 4, null, null, 200, null, null, null, null, null);
        String programBody = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(programRequest)))
                .andReturn().getResponse().getContentAsString();
        Long programId = objectMapper.readTree(programBody).get("id").asLong();

        String assignBody = "{\"electiveGroupId\":" + groupId + ",\"minRequired\":1,\"maxAllowed\":2}";
        mockMvc.perform(post("/programs/{id}/elective-groups", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(assignBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minRequired").value(1))
                .andExpect(jsonPath("$.maxAllowed").value(2));
    }

    @Test
    void gettingElectiveGroupDetailAndListingByProgramReturn200() throws Exception {
        String token = tokenWithRole("admin-academico6", "ADMINISTRATIVO");

        CreateElectiveGroupRequest groupRequest = new CreateElectiveGroupRequest("Optativas", null);
        String groupBody = mockMvc.perform(post("/elective-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupBody).get("id").asLong();

        mockMvc.perform(get("/elective-groups/{id}", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Optativas"));

        CreateProgramRequest programRequest = new CreateProgramRequest(
                "Tecnicatura Optativas", 4, null, null, 100, null, null, null, null, null);
        String programBody = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(programRequest)))
                .andReturn().getResponse().getContentAsString();
        Long programId = objectMapper.readTree(programBody).get("id").asLong();

        String assignBody = "{\"electiveGroupId\":" + groupId + ",\"minRequired\":1,\"maxAllowed\":3}";
        mockMvc.perform(post("/programs/{id}/elective-groups", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(assignBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/programs/{id}/elective-groups", programId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].maxAllowed").value(3));
    }

    @Test
    void addingPrerequisiteWithMinGradeOutOfRangeReturns400() throws Exception {
        String token = tokenWithRole("admin-academico5", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Tecnicatura para min grade");
        program.setTotalYears(3);
        entityManager.persist(program);

        Subject subjectA = new Subject();

        subjectA.setStatus(SubjectStatus.ACTIVE);
        subjectA.setCode(TestSubjectCodes.next());
        subjectA.setName("Materia MinGrade A");
        entityManager.persist(subjectA);
        Subject subjectB = new Subject();
        subjectB.setStatus(SubjectStatus.ACTIVE);
        subjectB.setCode(TestSubjectCodes.next());
        subjectB.setName("Materia MinGrade B");
        entityManager.persist(subjectB);

        ProgramSubject entradaA = new ProgramSubject();
        entradaA.setProgram(program);
        entradaA.setSubject(subjectA);
        entradaA.setCurricularYear(1);
        entradaA.setCurricularTerm(CurricularTerm.SECOND);
        entradaA.setCredits(5);
        entradaA.setStatus(StatusType.ACTIVE);
        entityManager.persist(entradaA);

        ProgramSubject entradaB = new ProgramSubject();
        entradaB.setProgram(program);
        entradaB.setSubject(subjectB);
        entradaB.setCurricularYear(1);
        entradaB.setCurricularTerm(CurricularTerm.FIRST);
        entradaB.setCredits(5);
        entradaB.setStatus(StatusType.ACTIVE);
        entityManager.persist(entradaB);
        entityManager.flush();

        AddPrerequisiteRequest request = new AddPrerequisiteRequest(
                entradaB.getId(), RequirementType.APPROVED, BigDecimal.valueOf(99.0), null);

        mockMvc.perform(post("/program-subjects/{id}/prerequisites", entradaA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatingAndReactivatingProgramWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("admin-baja1", "ADMINISTRATIVO");
        CreateProgramRequest request = new CreateProgramRequest("Tecnicatura Baja", 3, null, null, 100, null, null, null, null, null);
        String body = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/programs/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/programs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/programs").param("status", "ACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mockMvc.perform(get("/programs").param("status", "INACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());

        mockMvc.perform(post("/programs/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/programs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deactivatingProgramWithoutAdministrativeRoleReturns403() throws Exception {
        String adminToken = tokenWithRole("admin-baja2", "ADMINISTRATIVO");
        String body = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateProgramRequest("Tecnicatura Baja 2", 3, null, null, 100, null, null, null, null, null))))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        String preceptorToken = tokenWithRole("preceptor-baja2", "PRECEPTOR");
        mockMvc.perform(post("/programs/{id}/deactivate", id).header("Authorization", "Bearer " + preceptorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingAndReactivatingCourseWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("admin-curso1", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para curso baja");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        CreateCourseRequest request = new CreateCourseRequest(
                "Curso Baja", term.getId(), null, 20, null, 3, null, null, null);
        String body = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/courses/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/courses").param("status", "ACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        mockMvc.perform(post("/courses/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/courses/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void creatingCourseWithNonExistentTermReturns404() throws Exception {
        String token = tokenWithRole("admin-curso2", "ADMINISTRATIVO");
        CreateCourseRequest request = new CreateCourseRequest(
                "Curso sin term", 999999L, null, 10, null, 3, null, null, null);

        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingCourseWithoutAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("preceptor-curso1", "PRECEPTOR");
        CreateCourseRequest request = new CreateCourseRequest(
                "Curso sin permiso", 1L, null, 10, null, 3, null, null, null);

        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private Long createTermAs(String token, int year, String type) throws Exception {
        String body = "{\"year\":" + year + ",\"description\":\"Term " + year + "\",\"type\":" + type
                + ",\"startDate\":\"" + year + "-03-01T00:00:00\",\"endDate\":\"" + year + "-12-15T00:00:00\"}";
        String response = mockMvc.perform(post("/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private int createTermStatus(String token, int year, String type) throws Exception {
        String body = "{\"year\":" + year + ",\"description\":\"Term " + year + "\",\"type\":" + type
                + ",\"startDate\":\"" + year + "-03-01T00:00:00\",\"endDate\":\"" + year + "-12-15T00:00:00\"}";
        return mockMvc.perform(post("/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void creatingTermReturnsTheCatalogTypeInTheResponse() throws Exception {
        String token = tokenWithRole("preceptor-term-type1", "PRECEPTOR");
        Long id = createTermAs(token, 2061, "\"SECOND_SEMESTER\"");

        mockMvc.perform(get("/terms/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.type").value("SECOND_SEMESTER"));
    }

    @Test
    void creatingTermWithATypeOutsideTheCatalogReturns400() throws Exception {
        String token = tokenWithRole("preceptor-term-type2", "PRECEPTOR");

        assertThat(createTermStatus(token, 2062, "\"1C\"")).isEqualTo(400);
        assertThat(createTermStatus(token, 2062, "null")).isEqualTo(400);
    }

    @Test
    void creatingASecondActiveTermOfTheSameYearAndTypeReturns409ButAnotherTypeIsAllowed() throws Exception {
        String token = tokenWithRole("preceptor-term-type3", "PRECEPTOR");
        createTermAs(token, 2063, "\"FIRST_SEMESTER\"");

        assertThat(createTermStatus(token, 2063, "\"FIRST_SEMESTER\"")).isEqualTo(409);
        assertThat(createTermStatus(token, 2063, "\"SECOND_SEMESTER\"")).isEqualTo(201);
        assertThat(createTermStatus(token, 2064, "\"FIRST_SEMESTER\"")).isEqualTo(201);
    }

    @Test
    void aDeactivatedTermDoesNotCountForUniquenessButReactivatingItWhileAnotherIsActiveReturns409() throws Exception {
        String token = tokenWithRole("preceptor-term-type4", "PRECEPTOR");
        Long first = createTermAs(token, 2065, "\"ANNUAL\"");
        mockMvc.perform(post("/terms/{id}/deactivate", first).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Long second = createTermAs(token, 2065, "\"ANNUAL\"");

        mockMvc.perform(post("/terms/{id}/activate", first).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/terms/{id}/activate", second).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivatingAndReactivatingTermWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("preceptor-term1", "PRECEPTOR");
        CreateTermRequest request = new CreateTermRequest(
                2026, "Term Baja", TermType.FIRST_SEMESTER, LocalDateTime.now(), LocalDateTime.now().plusMonths(4), null, null);
        String body = mockMvc.perform(post("/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/terms/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/terms").param("status", "INACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());

        mockMvc.perform(post("/terms/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/terms/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void creatingTermWithoutPreceptorOrAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("alumno-term1", "ALUMNO");
        CreateTermRequest request = new CreateTermRequest(
                2026, "Term sin permiso", TermType.FIRST_SEMESTER, LocalDateTime.now(), LocalDateTime.now().plusMonths(4), null, null);

        mockMvc.perform(post("/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingAndReactivatingSubjectWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("admin-subject-baja1", "ADMINISTRATIVO");
        String directorToken = tokenWithRole("director-subject-baja1", "DIRECTOR");
        CreateSubjectRequest request = new CreateSubjectRequest("Materia para baja", "MBAJ", null, 4, null, GradeMode.NUMERIC, null);
        String body = mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/subjects/{id}/submit-for-approval", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/subjects/{id}/approve", id).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/subjects/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/subjects").param("status", "ACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        mockMvc.perform(post("/subjects/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/subjects/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void addingSubjectWithoutAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("preceptor-programsubject1", "PRECEPTOR");
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                1L, ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 5, null, null, null);

        mockMvc.perform(post("/programs/{id}/subjects", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingAndReactivatingProgramSubjectWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("admin-programsubject1", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Tecnicatura ProgramSubject Baja");
        program.setTotalYears(3);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia ProgramSubject Baja");
        entityManager.persist(subject);
        entityManager.flush();

        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 5, null, null, null);
        String body = mockMvc.perform(post("/programs/{id}/subjects", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/program-subjects/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/programs/{id}/subjects", program.getId())
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mockMvc.perform(get("/programs/{id}/subjects", program.getId())
                        .param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());

        mockMvc.perform(post("/program-subjects/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/program-subjects/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void addingModuleWithAdministrativeRoleReturns201() throws Exception {
        String token = tokenWithRole("admin-coursesubject1", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para módulo");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        String courseBody = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest(
                                "Curso con módulos", term.getId(), null, 15, null, 3, null, null, null))))
                .andReturn().getResponse().getContentAsString();
        Long courseId = objectMapper.readTree(courseBody).get("id").asLong();

        CreateCourseSubjectRequest request = new CreateCourseSubjectRequest("Módulo 1", 1, 4, null, null);

        mockMvc.perform(post("/courses/{id}/subjects", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Módulo 1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void addingModuleWithoutAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("preceptor-coursesubject1", "PRECEPTOR");
        CreateCourseSubjectRequest request = new CreateCourseSubjectRequest("Módulo sin permiso", 1, 4, null, null);

        mockMvc.perform(post("/courses/{id}/subjects", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingAndReactivatingCourseSubjectWorksAndTheListingFiltersByStatus() throws Exception {
        String token = tokenWithRole("admin-coursesubject2", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para módulo baja");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        String courseBody = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest(
                                "Curso con módulo baja", term.getId(), null, 15, null, 3, null, null, null))))
                .andReturn().getResponse().getContentAsString();
        Long courseId = objectMapper.readTree(courseBody).get("id").asLong();

        String moduleBody = mockMvc.perform(post("/courses/{id}/subjects", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseSubjectRequest("Módulo Baja", 1, 4, null, null))))
                .andReturn().getResponse().getContentAsString();
        Long moduleId = objectMapper.readTree(moduleBody).get("id").asLong();

        mockMvc.perform(post("/course-subjects/{id}/deactivate", moduleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/courses/{id}/subjects", courseId)
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + moduleId + ")]").isEmpty());

        mockMvc.perform(post("/course-subjects/{id}/activate", moduleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/course-subjects/{id}", moduleId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updatingElectiveGroupUpdatesNameAndDescription() throws Exception {
        String token = tokenWithRole("admin-editar-grupo", "ADMINISTRATIVO");
        String body = mockMvc.perform(post("/elective-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateElectiveGroupRequest("Nombre Original", null))))
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/elective-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\":\"Nombre Editado\",\"description\":\"nueva desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre Editado"));
    }

    @Test
    void removingElectiveGroupFromProgramWithoutAssociatedSubjectsWorks() throws Exception {
        String token = tokenWithRole("admin-quitar-grupo1", "ADMINISTRATIVO");
        String groupBody = mockMvc.perform(post("/elective-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateElectiveGroupRequest("Grupo a quitar", null))))
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupBody).get("id").asLong();

        String programBody = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateProgramRequest("Tecnicatura quitar grupo", 3, null, null, 50, null, null, null, null, null))))
                .andReturn().getResponse().getContentAsString();
        Long programId = objectMapper.readTree(programBody).get("id").asLong();

        mockMvc.perform(post("/programs/{id}/elective-groups", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"electiveGroupId\":" + groupId + ",\"minRequired\":1,\"maxAllowed\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/programs/{id}/elective-groups/{groupId}", programId, groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/programs/{id}/elective-groups", programId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingElectiveGroupFromProgramWithAssociatedSubjectsReturns409() throws Exception {
        String token = tokenWithRole("admin-quitar-grupo2", "ADMINISTRATIVO");
        String groupBody = mockMvc.perform(post("/elective-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateElectiveGroupRequest("Grupo en uso", null))))
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupBody).get("id").asLong();

        Program program = new Program();
        program.setName("Tecnicatura grupo en uso");
        program.setTotalYears(3);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia del grupo en uso");
        entityManager.persist(subject);
        entityManager.flush();

        mockMvc.perform(post("/programs/{id}/elective-groups", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"electiveGroupId\":" + groupId + ",\"minRequired\":1,\"maxAllowed\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/programs/{id}/subjects", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateProgramSubjectRequest(
                                subject.getId(), ProgramSubjectCategory.ELECTIVE, null, null, 5, null, null, groupId))))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/programs/{id}/elective-groups/{groupId}", program.getId(), groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void settingCatalogVisibilityWithoutAdminRoleReturns403() throws Exception {
        String token = tokenWithRole("preceptor-visibility1", "PRECEPTOR");

        mockMvc.perform(put("/programs/{id}/catalog-visibility", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"visible\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingCatalogVisibilityWithAdminRoleUpdatesAndReturns200() throws Exception {
        String token = tokenWithRole("admin-visibility1", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Carrera Visibilidad Catalog");
        program.setTotalYears(2);
        entityManager.persist(program);
        entityManager.flush();

        mockMvc.perform(put("/programs/{id}/catalog-visibility", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleInCatalog").value(true));
    }

    @Test
    void addingMandatorySubjectWithoutCurricularYearReturns400() throws Exception {
        String token = tokenWithRole("admin-cat-mandatory1", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Carrera Category Mandatory");
        program.setTotalYears(2);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia Cat Mandatory");
        entityManager.persist(subject);
        entityManager.flush();

        var request = new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, null, null, 5, null, null, null);

        mockMvc.perform(post("/programs/{id}/subjects", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingElectiveSubjectWithoutElectiveGroupIdReturns400() throws Exception {
        String token = tokenWithRole("admin-cat-elective1", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Carrera Category Elective");
        program.setTotalYears(2);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia Cat Elective");
        entityManager.persist(subject);
        entityManager.flush();

        var request = new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.ELECTIVE, null, null, 5, null, null, null);

        mockMvc.perform(post("/programs/{id}/subjects", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
