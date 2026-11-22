package com.red_escuela.backend.graduation.certificate.regular;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RetiredRegularCertificateRouteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void retiredProgramEnrollmentRegularCertificateRouteReturnsNotFound() throws Exception {
        String token = jwtService.generateAccessToken(persistStudentPerson());

        mockMvc.perform(get("/me/program-enrollments/1/certificates/regular").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private Person persistStudentPerson() {
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String suffix = UUID.randomUUID().toString();
            person.setName("Persona");
            person.setLastname(suffix);
            person.setDni(suffix.substring(0, 12));
            person.setUsername("retired." + suffix);
            person.setEmail("retired." + suffix + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            Role role = roleRepository.findByName("ALUMNO").orElseThrow();
            person.getRoles().add(role);
            entityManager.persist(person);
            entityManager.flush();
            return person;
        });
    }
}
