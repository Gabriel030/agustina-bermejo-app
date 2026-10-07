package com.red_escuela.backend.characterization.academicorg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class AcademicOrgCharacterizationSupport extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

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
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    protected Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        entityManager.flush();
        return term;
    }

    protected String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    protected ResultActions postJson(Person actor, String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions postEmpty(Person actor, String path) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(actor)));
    }

    protected ResultActions putJson(Person actor, String path, Object body) throws Exception {
        return mockMvc.perform(put(path)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions getAs(Person actor, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(actor)));
    }

    protected long idOf(ResultActions result) throws Exception {
        JsonNode node = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    protected CreateSubjectRequest completeSubjectRequest(String name) {
        return new CreateSubjectRequest(name, TestSubjectCodes.next(), "Descripcion", 4, 0, GradeMode.NUMERIC, 3);
    }

    protected long countRows(String entityName, String where) {
        return entityManager.createQuery("select count(e) from " + entityName + " e where " + where, Long.class)
                .getSingleResult();
    }
}
