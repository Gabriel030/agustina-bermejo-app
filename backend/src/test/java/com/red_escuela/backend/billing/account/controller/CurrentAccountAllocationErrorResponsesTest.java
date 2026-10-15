package com.red_escuela.backend.billing.account.controller;

import com.red_escuela.backend.billing.account.exception.PaymentAllocationRejectedException;
import com.red_escuela.backend.billing.account.usecase.AllocateAccountCreditUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-089")
@Tag("D-139")
class CurrentAccountAllocationErrorResponsesTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private AllocateAccountCreditUseCase allocateAccountCreditUseCase;

    private String administrativeToken() {
        String username = "administrativo.errors." + System.nanoTime();
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
        person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    @Test
    void aPlanThatBreaksTheAllocationOrderAnswersConflictWithTheViolationCode() throws Exception {
        when(allocateAccountCreditUseCase.allocateCreditOfStudentAccounts(anyLong())).thenThrow(new PaymentAllocationRejectedException(
                List.of(new PolicyViolation("ALLOCATION_ORDER_VIOLATED", "The plan skips an older invoice"))));

        mockMvc.perform(post("/students/12/current-account/allocations").header("Authorization", "Bearer " + administrativeToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations[0].code").value("ALLOCATION_ORDER_VIOLATED"));
    }

    @Test
    void aStudentIdThatIsNotANumberAnswersBadRequest() throws Exception {
        mockMvc.perform(post("/students/not-a-number/current-account/allocations").header("Authorization", "Bearer " + administrativeToken()))
                .andExpect(status().isBadRequest());
    }
}
