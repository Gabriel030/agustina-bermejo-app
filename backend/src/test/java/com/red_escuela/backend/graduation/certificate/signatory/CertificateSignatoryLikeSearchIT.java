package com.red_escuela.backend.graduation.certificate.signatory;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class CertificateSignatoryLikeSearchIT extends AbstractIntegrationTest {

    private static final String BASE = "/certificate-signatories";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    private String director;

    @BeforeEach
    void createDirector() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Firmante");
        person.setLastname("Director");
        person.setUsername("signatory.like.director");
        person.setDni("92100001");
        person.setEmail("signatory.like.director@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("DIRECTOR").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        director = jwtService.generateAccessToken(person);
    }

    private void create(String name) throws Exception {
        mockMvc.perform(post(BASE).header("Authorization", "Bearer " + director).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"role\":\"Rectora\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void percentInSearchTermIsLiteralAndDoesNotMatchEverything() throws Exception {
        create("Zeta Uno");
        create("Zeta 50% Dos");

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + director).param("q", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Zeta 50% Dos"));
    }

    @Test
    void underscoreInSearchTermIsLiteral() throws Exception {
        create("Zeta Uno");
        create("Zeta_Dos");

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + director).param("q", "zeta_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Zeta_Dos"));
    }
}
