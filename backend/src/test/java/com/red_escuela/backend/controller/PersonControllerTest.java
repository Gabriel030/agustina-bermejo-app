package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonControllerTest extends AbstractIntegrationTest {

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

    private Person persistPersonWithRole(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Nombre");
        person.setLastname("Apellido");
        person.setUsername(username);
        person.setDni(dni);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    @Test
    void putMeUpdatesOnlyTheOwnPersonAndNeverTheDni() throws Exception {
        Person own = persistPersonWithRole("propio1", "60111222", "ALUMNO");
        Person other = persistPersonWithRole("otro1", "60333444", "ALUMNO");
        String token = jwtService.generateAccessToken(own);

        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                "nuevo.email@example.com", "1155556666", null, "Calle Nueva", "10", null, "Ciudad", null, null, null);

        mockMvc.perform(put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo.email@example.com"))
                .andExpect(jsonPath("$.telefono").value("1155556666"));

        entityManager.flush();
        entityManager.clear();
        Person updatedOwn = personRepository.findById(own.getId()).orElseThrow();
        assertThat(updatedOwn.getEmail()).isEqualTo("nuevo.email@example.com");
        assertThat(updatedOwn.getPhoneNumber()).isEqualTo("1155556666");
        assertThat(updatedOwn.getDni()).isEqualTo("60111222");

        Person otherUnchanged = personRepository.findById(other.getId()).orElseThrow();
        assertThat(otherUnchanged.getEmail()).isEqualTo("otro1@example.com");
        assertThat(otherUnchanged.getPhoneNumber()).isNull();
    }

    @Test
    void putMeUpdatesTheDniOwn() throws Exception {
        Person own = persistPersonWithRole("propio3", "60777888", "ALUMNO");
        String token = jwtService.generateAccessToken(own);

        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                null, null, "60999000", null, null, null, null, null, null, null);

        mockMvc.perform(put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("60999000"));

        entityManager.flush();
        entityManager.clear();
        Person updatedOwn = personRepository.findById(own.getId()).orElseThrow();
        assertThat(updatedOwn.getDni()).isEqualTo("60999000");
    }

    @Test
    void putMeWithDniAlreadyExistingReturns400() throws Exception {
        persistPersonWithRole("propio4", "60111333", "ALUMNO");
        Person own = persistPersonWithRole("propio5", "60222444", "ALUMNO");
        String token = jwtService.generateAccessToken(own);

        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                null, null, "60111333", null, null, null, null, null, null, null);

        mockMvc.perform(put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMeReturnsProfileOwnWithRoles() throws Exception {
        Person own = persistPersonWithRole("propio2", "60555666", "PRECEPTOR");
        String token = jwtService.generateAccessToken(own);

        mockMvc.perform(get("/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.getId()))
                .andExpect(jsonPath("$.nombre").value("Nombre"))
                .andExpect(jsonPath("$.roles[0]").value("PRECEPTOR"));
    }

    @Test
    void getMeWithoutAuthReturns401OrForbidden() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void putMeWithoutAuthReturns401OrForbidden() throws Exception {
        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                "x@example.com", null, null, null, null, null, null, null, null, null);

        mockMvc.perform(put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
