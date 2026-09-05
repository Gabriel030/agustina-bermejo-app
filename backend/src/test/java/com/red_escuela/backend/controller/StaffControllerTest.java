package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateStaffRequest;
import com.red_escuela.backend.dto.UpdateStaffRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffControllerTest extends AbstractIntegrationTest {

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
    private PersonRepository personRepository;

    @Autowired
    private InstructorRepository instructorRepository;

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

    private CreateStaffRequest staffRequest(String email, String dni, String role) {
        return new CreateStaffRequest(
                "Nombre", "Apellido", email, dni,
                "Calle", "1", null, "Ciudad", null, null, null, role);
    }

    @Test
    void onboardingOfTeacherCreatesPersonAndInstructorAndAssignsRole() throws Exception {
        String token = tokenWithRole("admin1", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("docente.nuevo@example.com", "50111222", "DOCENTE");

        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCENTE"))
                .andReturn().getResponse().getContentAsString();

        Long personId = objectMapper.readTree(body).get("id").asLong();
        Person person = personRepository.findById(personId).orElseThrow();
        assertThat(person.getRoles()).extracting(Role::getName).contains("DOCENTE");
        assertThat(instructorRepository.findAll())
                .anyMatch(instructor -> instructor.getPerson().getId().equals(personId));
    }

    @Test
    void onboardingOfPreceptorNotCreatesInstructor() throws Exception {
        String token = tokenWithRole("admin2", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("preceptor.nuevo@example.com", "50333444", "PRECEPTOR");

        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long personId = objectMapper.readTree(body).get("id").asLong();
        assertThat(instructorRepository.findAll())
                .noneMatch(instructor -> instructor.getPerson().getId().equals(personId));
    }

    @Test
    void onboardingWithRoleInvalidReturns400() throws Exception {
        String token = tokenWithRole("admin3", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("rol.invalido@example.com", "50555666", "DIRECTOR");

        mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editStaffWithoutRoleAdministrativeReturns403() throws Exception {
        String adminToken = tokenWithRole("admin4", "ADMINISTRATIVO");
        CreateStaffRequest createRequest = staffRequest("editable@example.com", "50777888", "DOCENTE");
        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long personId = objectMapper.readTree(body).get("id").asLong();

        String nonAdminToken = tokenWithRole("preceptor-no-admin", "PRECEPTOR");
        UpdateStaffRequest updateRequest = new UpdateStaffRequest(
                "nuevo@example.com", "1199998888", null, null, null, null, null, null, null);

        mockMvc.perform(put("/staff/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + nonAdminToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void editStaffUpdatesContactAndNeverTheDni() throws Exception {
        String adminToken = tokenWithRole("admin5", "ADMINISTRATIVO");
        CreateStaffRequest createRequest = staffRequest("editable2@example.com", "50999000", "DOCENTE");
        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long personId = objectMapper.readTree(body).get("id").asLong();

        UpdateStaffRequest updateRequest = new UpdateStaffRequest(
                "actualizado@example.com", "1199998888", "Nueva Calle", "2", null, "Otra Ciudad", null, null, null);

        mockMvc.perform(put("/staff/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("actualizado@example.com"))
                .andExpect(jsonPath("$.dni").value("50999000"));
    }
}
