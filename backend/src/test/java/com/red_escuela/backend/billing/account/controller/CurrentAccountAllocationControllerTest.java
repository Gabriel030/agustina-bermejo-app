package com.red_escuela.backend.billing.account.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-089")
class CurrentAccountAllocationControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private String tokenWithRole(String roleName) {
        String username = roleName.toLowerCase() + ".alloc." + System.nanoTime();
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRATIVO", "DIRECTOR"})
    void administrativeStaffAndDirectorCanForceTheAllocationAndGetTheListOfAllocations(String role) throws Exception {
        mockMvc.perform(post("/students/987654321/current-account/allocations").header("Authorization", "Bearer " + tokenWithRole(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations").isArray())
                .andExpect(jsonPath("$.allocations").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUMNO", "DOCENTE", "PRECEPTOR"})
    void otherRolesCannotForceTheAllocation(String role) throws Exception {
        mockMvc.perform(post("/students/987654321/current-account/allocations").header("Authorization", "Bearer " + tokenWithRole(role)))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test
    void anonymousCallersAreRejected() throws Exception {
        mockMvc.perform(post("/students/987654321/current-account/allocations")).andExpect(status().isForbidden());
    }
}
