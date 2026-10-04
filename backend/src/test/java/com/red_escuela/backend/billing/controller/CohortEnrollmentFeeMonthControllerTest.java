package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CohortEnrollmentFeeMonthControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

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
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private ResultActions patchMonth(String token, int month) throws Exception {
        return mockMvc.perform(patch("/cohorts/{id}/enrollment-fee-month", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"enrollmentFeeMonth\":" + month + "}"));
    }

    @Test
    void aRoleWithoutPermissionGets403() throws Exception {
        patchMonth(tokenWithRole("alumno18", "ALUMNO"), 3).andExpect(status().isForbidden());
    }

    @Test
    void monthZeroIsRejectedWith400() throws Exception {
        patchMonth(tokenWithRole("director18a", "DIRECTOR"), 0).andExpect(status().isBadRequest());
    }

    @Test
    void monthThirteenIsRejectedWith400() throws Exception {
        patchMonth(tokenWithRole("director18b", "DIRECTOR"), 13).andExpect(status().isBadRequest());
    }
}
