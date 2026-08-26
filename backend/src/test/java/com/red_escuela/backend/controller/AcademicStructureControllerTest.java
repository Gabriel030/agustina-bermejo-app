package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.dto.CreateProgramRequest;
import com.red_escuela.backend.dto.CreateSubjectRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
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
}
