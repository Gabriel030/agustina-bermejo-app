package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.dto.CreateProgramRequest;
import com.red_escuela.backend.dto.CreateSubjectRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.RequirementType;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.service.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
    void crearCarreraConCreditosYVentanaDeAdmisionPersisteTodosLosCampos() throws Exception {
        String token = tokenWithRole("admin-academico3", "ADMINISTRATIVO");
        CreateProgramRequest request = new CreateProgramRequest(
                "Tecnicatura en Sistemas", 3, null, 240, null, null);

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
                "Tecnicatura con Electivas", 4, null, 200, null, null);
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
                "Tecnicatura Optativas", 4, null, 100, null, null);
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
        subjectA.setName("Materia MinGrade A");
        entityManager.persist(subjectA);
        Subject subjectB = new Subject();
        subjectB.setName("Materia MinGrade B");
        entityManager.persist(subjectB);

        ProgramSubject entradaA = new ProgramSubject();
        entradaA.setProgram(program);
        entradaA.setSubject(subjectA);
        entradaA.setCurricularYear(1);
        entradaA.setCurricularTerm(CurricularTerm.FIRST);
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
                entradaB.getId(), RequirementType.APPROVED, 99.0, null);

        mockMvc.perform(post("/program-subjects/{id}/prerequisites", entradaA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
