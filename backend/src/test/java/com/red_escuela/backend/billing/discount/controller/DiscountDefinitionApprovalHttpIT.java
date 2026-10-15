package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("D-217")
class DiscountDefinitionApprovalHttpIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);
    private static final String BASE = "/discount-definitions";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    private Person director;
    private Person administrative;
    private Person otherAdministrative;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        director = persistPerson("approval.director", "DIRECTOR");
        administrative = persistPerson("approval.administrative", "ADMINISTRATIVO");
        otherAdministrative = persistPerson("approval.other", "ADMINISTRATIVO");
    }

    @AfterEach
    void tearDown() {
        String people = director.getId() + "," + administrative.getId() + "," + otherAdministrative.getId();
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        try {
            jdbc.update("delete from discount_definition_status_change where discount_definition_id in (select id from discount_definition where created_by_id in (" + people + "))");
            jdbc.update("delete from discount_definition where created_by_id in (" + people + ")");
        } finally {
            jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
        }
        jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id in (" + people + "))");
        jdbc.update("delete from authorization_request where requested_by_id in (" + people + ")");
    }

    private Person persistPerson(String prefix, String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return personRepository.save(person);
        });
    }

    private String tokenOf(Person person) {
        return jwtService.generateAccessToken(person);
    }

    private static Map<String, Object> annualUpfront(String name) {
        Map<String, Object> calculation = new LinkedHashMap<>();
        calculation.put("type", "PROPORTIONAL");
        calculation.put("ratePercent", 15);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("discountType", "ANNUAL_UPFRONT");
        body.put("applicableInvoiceType", "ANNUAL_UPFRONT");
        body.put("calculation", calculation);
        body.put("availableFrom", TODAY.toString());
        body.put("priority", 1);
        return body;
    }

    private ResultActions postJson(String path, Person caller, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + tokenOf(caller)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Long proposedBy(Person proposer, Map<String, Object> body) throws Exception {
        JsonNode response = objectMapper.readTree(postJson(BASE, proposer, body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        return response.get("authorizationRequestId").asLong();
    }

    private ResultActions decide(String action, Long requestId, Person decider) throws Exception {
        return postJson("/authorization-requests/" + requestId + "/" + action, decider, Map.of("decisionReason", "de acuerdo"));
    }

    private JsonNode catalogEntry(String name) throws Exception {
        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get(BASE).header("Authorization", "Bearer " + tokenOf(director))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(catalog.spliterator(), false).filter(entry -> name.equals(entry.get("name").asText())).findFirst().orElse(null);
    }

    @Test
    @Tag("D-176")
    void a09TheProposedDefinitionExistsOnlyAfterTheDirectorApprovesIt() throws Exception {
        Long requestId = proposedBy(administrative, annualUpfront("a09 aprobada por http"));

        assertThat(catalogEntry("a09 aprobada por http")).isNull();
        decide("approve", requestId, director).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        JsonNode approved = catalogEntry("a09 aprobada por http");
        assertThat(approved).isNotNull();
        assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(approved.get("directPublication").asBoolean()).isFalse();
        assertThat(approved.get("authorizationRequestId").asLong()).isEqualTo(requestId);
        assertThat(approved.get("createdById").asLong()).isEqualTo(administrative.getId());
    }

    @Test
    @Tag("D-225")
    void a11OnlyTheProposerWithdrawsAPendingProposalAndNobodyWithdrawsAnApprovedOne() throws Exception {
        Long withdrawnId = proposedBy(administrative, annualUpfront("a11 retirada"));

        decide("withdraw", withdrawnId, otherAdministrative).andExpect(status().isForbidden());
        decide("withdraw", withdrawnId, administrative).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));

        Long approvedId = proposedBy(administrative, annualUpfront("a11 aprobada"));
        decide("approve", approvedId, director).andExpect(status().isOk());
        decide("withdraw", approvedId, administrative).andExpect(status().isConflict());

        assertThat(catalogEntry("a11 retirada")).isNull();
        assertThat(catalogEntry("a11 aprobada")).isNotNull();
    }

    @Test
    @Tag("D-036")
    void anApprovedClosureClosesTheDefinitionWithTheProposedReason() throws Exception {
        Long definitionId = objectMapper.readTree(postJson(BASE, director, annualUpfront("cierre propuesto")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        Map<String, Object> reason = Map.of("reason", "  Cambio de política  ");
        Long requestId = objectMapper.readTree(postJson(BASE + "/" + definitionId + "/closure", administrative, reason).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).get("authorizationRequestId").asLong();

        assertThat(catalogEntry("cierre propuesto").get("status").asText()).isEqualTo("ACTIVE");
        decide("approve", requestId, director).andExpect(status().isOk());

        JsonNode closed = catalogEntry("cierre propuesto");
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("closureReason").asText()).isEqualTo("Cambio de política");
        assertThat(closed.get("closedById").asLong()).isEqualTo(director.getId());
    }
}
