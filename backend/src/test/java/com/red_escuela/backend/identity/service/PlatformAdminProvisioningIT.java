package com.red_escuela.backend.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("D-484")
class PlatformAdminProvisioningIT extends AbstractIntegrationTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PersonFactory personFactory;

    @Autowired
    private PasswordPolicyValidator passwordPolicyValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void platformAdminRoleExistsAfterMigrations() {
        assertThat(roleRepository.findByName("PLATFORM_ADMIN")).isPresent();
    }

    @Test
    void provisioningCreatesTheSingleAccountWithAHashedPassword() {
        provisionerFor("owner", "owner@platform.example", "Sup3rSecreta").run(null);

        Person admin = personRepository.findByUsername("owner").orElseThrow();
        assertThat(admin.getRoles()).extracting(Role::getName).containsExactly("PLATFORM_ADMIN");
        assertThat(admin.getPassword()).isNotEqualTo("Sup3rSecreta");
        assertThat(passwordEncoder.matches("Sup3rSecreta", admin.getPassword())).isTrue();
        assertThat(personRepository.existsByRoles_Name("PLATFORM_ADMIN")).isTrue();
    }

    @Test
    void provisioningAgainNeverCreatesASecondPlatformAdmin() {
        provisionerFor("owner", "owner@platform.example", "Sup3rSecreta").run(null);
        long accountsAfterFirstRun = personRepository.count();

        provisionerFor("second", "second@platform.example", "Sup3rSecreta").run(null);

        assertThat(personRepository.count()).isEqualTo(accountsAfterFirstRun);
        assertThat(personRepository.findByUsername("second")).isEmpty();
    }

    @Test
    void provisioningWithAWeakPasswordIsRejectedAndCreatesNothing() {
        long before = personRepository.count();

        assertThatThrownBy(() -> provisionerFor("owner", "owner@platform.example", "abc").run(null))
                .isInstanceOf(InvalidPasswordPolicyException.class);

        assertThat(personRepository.count()).isEqualTo(before);
    }

    @Test
    void directorCannotCreateAStaffMemberWithThePlatformAdminRole() throws Exception {
        String token = tokenWithRole("director.platform.check", "DIRECTOR");
        CreateStaffRequest request = new CreateStaffRequest(
                "Nombre", "Apellido", "intruso@example.com", "50999888", null,
                "Calle", "1", null, "Ciudad", null, null, null, "PLATFORM_ADMIN");

        mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(personRepository.existsByRoles_Name("PLATFORM_ADMIN")).isFalse();
    }

    private PlatformAdminProvisioner provisionerFor(String username, String email, String password) {
        return new PlatformAdminProvisioner(personRepository, roleRepository, personFactory, passwordPolicyValidator,
                passwordEncoder, username, email, password);
    }

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
}
