package com.red_escuela.backend.characterization.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.AuthResponse;
import com.red_escuela.backend.identity.dto.LoginRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class AccessCharacterizationSupport extends AbstractIntegrationTest {

    static final String PASSWORD = "Correcta123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected JwtService jwtService;

    protected Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Nombre");
        person.setLastname("Apellido");
        person.setUsername(username);
        person.setDni(dni);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(PASSWORD));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    protected String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions login(String username, String password) throws Exception {
        return postJson("/auth/login", new LoginRequest(username, password));
    }

    protected AuthResponse loginSuccessfully(String username, String password) throws Exception {
        String body = login(username, password).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class);
    }

    protected long countRows(String entityName, String where) {
        return entityManager.createQuery("select count(e) from " + entityName + " e where " + where, Long.class)
                .getSingleResult();
    }
}
