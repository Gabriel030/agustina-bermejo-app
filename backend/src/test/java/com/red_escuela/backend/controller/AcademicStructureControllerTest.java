package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateCourseRequest;
import com.red_escuela.backend.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.dto.CreateProgramRequest;
import com.red_escuela.backend.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.dto.CreateSubjectRequest;
import com.red_escuela.backend.dto.CreateTermRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.RequirementType;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.Term;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void crearSubjectConRolAdministrativoDevuelve201() throws Exception {
        String token = tokenWithRole("admin-academico1", "ADMINISTRATIVO");
        CreateSubjectRequest request = new CreateSubjectRequest("Química General", null);

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Química General"));
    }

    @Test
    void crearSubjectSinRolAdministrativoDevuelve403() throws Exception {
        String token = tokenWithRole("preceptor-academico1", "PRECEPTOR");
        CreateSubjectRequest request = new CreateSubjectRequest("Biología", null);

        mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearSubjectConNombreDuplicadoDevuelve409() throws Exception {
        String token = tokenWithRole("admin-academico2", "ADMINISTRATIVO");
        CreateSubjectRequest request = new CreateSubjectRequest("Historia", null);

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
    void crearCarreraConCreditosPersisteTodosLosCampos() throws Exception {
        String token = tokenWithRole("admin-academico3", "ADMINISTRATIVO");
        CreateProgramRequest request = new CreateProgramRequest(
                "Tecnicatura en Sistemas", 3, null, null, 240);

        mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tecnicatura en Sistemas"))
                .andExpect(jsonPath("$.totalCreditsRequired").value(240));
    }

    @Test
    void listarProgramasConCualquierRolAutenticadoDevuelve200() throws Exception {
        String token = tokenWithRole("alumno-academico1", "ALUMNO");

        mockMvc.perform(get("/programs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void crearGrupoElectivoYAsignarRangoACarrera() throws Exception {
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
                "Tecnicatura con Electivas", 4, null, null, 200);
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
    void verDetalleDeGrupoElectivoYListarPorCarreraDevuelven200() throws Exception {
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
                "Tecnicatura Optativas", 4, null, null, 100);
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
    void agregarCorrelatividadConMinGradeFueraDeRangoDevuelve400() throws Exception {
        String token = tokenWithRole("admin-academico5", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Tecnicatura para min grade");
        program.setTotalYears(3);
        entityManager.persist(program);

        Subject subjectA = new Subject();
        subjectA.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subjectA.setName("Materia MinGrade A");
        entityManager.persist(subjectA);
        Subject subjectB = new Subject();
        subjectB.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
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

        // entradaB en el cuatrimestre ANTERIOR de entradaA (mismo año) — cronológicamente
        // válida como correlativa, para que este test ejercite específicamente la
        // validación de minGrade fuera de rango y no la de orden cronológico.
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
                entradaB.getId(), RequirementType.APPROVED, 99.0, null);

        mockMvc.perform(post("/program-subjects/{id}/prerequisites", entradaA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // Desactivación / reactivación / filtro por status
    // ---------------------------------------------------------------------

    @Test
    void desactivarYReactivarProgramaFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("admin-baja1", "ADMINISTRATIVO");
        CreateProgramRequest request = new CreateProgramRequest("Tecnicatura Baja", 3, null, null, 100);
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
    void desactivarProgramaSinRolAdministrativoDevuelve403() throws Exception {
        String adminToken = tokenWithRole("admin-baja2", "ADMINISTRATIVO");
        String body = mockMvc.perform(post("/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateProgramRequest("Tecnicatura Baja 2", 3, null, null, 100))))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        String preceptorToken = tokenWithRole("preceptor-baja2", "PRECEPTOR");
        mockMvc.perform(post("/programs/{id}/deactivate", id).header("Authorization", "Bearer " + preceptorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarYReactivarCursoFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("admin-curso1", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para curso baja");
        term.setType("1C");
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        CreateCourseRequest request = new CreateCourseRequest(
                "Curso Baja", term.getId(), null, 20, null, null, null, null);
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
    void crearCursoConTermInexistenteDevuelve404() throws Exception {
        String token = tokenWithRole("admin-curso2", "ADMINISTRATIVO");
        CreateCourseRequest request = new CreateCourseRequest(
                "Curso sin term", 999999L, null, 10, null, null, null, null);

        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearCursoSinRolAdministrativoDevuelve403() throws Exception {
        String token = tokenWithRole("preceptor-curso1", "PRECEPTOR");
        CreateCourseRequest request = new CreateCourseRequest(
                "Curso sin permiso", 1L, null, 10, null, null, null, null);

        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarYReactivarPeriodoFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("preceptor-term1", "PRECEPTOR");
        CreateTermRequest request = new CreateTermRequest(
                2026, "Term Baja", "1C", LocalDateTime.now(), LocalDateTime.now().plusMonths(4), null, null);
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
    void crearPeriodoSinRolPreceptorNiAdministrativoDevuelve403() throws Exception {
        String token = tokenWithRole("alumno-term1", "ALUMNO");
        CreateTermRequest request = new CreateTermRequest(
                2026, "Term sin permiso", "1C", LocalDateTime.now(), LocalDateTime.now().plusMonths(4), null, null);

        mockMvc.perform(post("/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarYReactivarSubjectFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("admin-subject-baja1", "ADMINISTRATIVO");
        CreateSubjectRequest request = new CreateSubjectRequest("Materia para baja", null);
        String body = mockMvc.perform(post("/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/subjects/{id}/deactivate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/subjects").param("status", "ACTIVE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        mockMvc.perform(post("/subjects/{id}/activate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/subjects/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------------------------------------------------------------------
    // ProgramSubject: desactivar/reactivar/filtro + roles a nivel HTTP
    // (antes solo estaban probados a nivel servicio, sin pasar por @PreAuthorize)
    // ---------------------------------------------------------------------

    @Test
    void agregarMateriaSinRolAdministrativoDevuelve403() throws Exception {
        String token = tokenWithRole("preceptor-programsubject1", "PRECEPTOR");
        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                1L, 1, CurricularTerm.FIRST, 5, null, null, null);

        mockMvc.perform(post("/programs/{id}/subjects", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarYReactivarProgramSubjectFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("admin-programsubject1", "ADMINISTRATIVO");

        Program program = new Program();
        program.setName("Tecnicatura ProgramSubject Baja");
        program.setTotalYears(3);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName("Materia ProgramSubject Baja");
        entityManager.persist(subject);
        entityManager.flush();

        CreateProgramSubjectRequest request = new CreateProgramSubjectRequest(
                subject.getId(), 1, CurricularTerm.FIRST, 5, null, null, null);
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

    // ---------------------------------------------------------------------
    // CourseSubject: CRUD, desactivar/reactivar/filtro y roles
    // (CourseSubjectController no tenía ningún test todavía)
    // ---------------------------------------------------------------------

    @Test
    void agregarModuloConRolAdministrativoDevuelve201() throws Exception {
        String token = tokenWithRole("admin-coursesubject1", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para módulo");
        term.setType("1C");
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        String courseBody = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest(
                                "Curso con módulos", term.getId(), null, 15, null, null, null, null))))
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
    void agregarModuloSinRolAdministrativoDevuelve403() throws Exception {
        String token = tokenWithRole("preceptor-coursesubject1", "PRECEPTOR");
        CreateCourseSubjectRequest request = new CreateCourseSubjectRequest("Módulo sin permiso", 1, 4, null, null);

        mockMvc.perform(post("/courses/{id}/subjects", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarYReactivarCourseSubjectFuncionaYElListadoFiltraPorStatus() throws Exception {
        String token = tokenWithRole("admin-coursesubject2", "ADMINISTRATIVO");
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term para módulo baja");
        term.setType("1C");
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(term);
        entityManager.flush();

        String courseBody = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest(
                                "Curso con módulo baja", term.getId(), null, 15, null, null, null, null))))
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

    // ---------------------------------------------------------------------
    // ElectiveGroup: editar y quitar de una carrera (con el caso "todavía en uso")
    // ---------------------------------------------------------------------

    @Test
    void editarGrupoElectivoActualizaNombreYDescripcion() throws Exception {
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
    void quitarGrupoElectivoDeCarreraSinMateriasAsociadasFunciona() throws Exception {
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
                                new CreateProgramRequest("Tecnicatura quitar grupo", 3, null, null, 50))))
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
    void quitarGrupoElectivoDeCarreraConMateriasAsociadasDevuelve409() throws Exception {
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
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
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
                                subject.getId(), 1, CurricularTerm.FIRST, 5, null, null, groupId))))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/programs/{id}/elective-groups/{groupId}", program.getId(), groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
