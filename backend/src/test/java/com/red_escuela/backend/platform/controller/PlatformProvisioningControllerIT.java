package com.red_escuela.backend.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class PlatformProvisioningControllerIT extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/platform/provisioning/first-director";
    private static final String STATUS_ENDPOINT = "/platform/provisioning/first-director/status";
    private static final String NEW_PASSWORD = "ClaveNueva456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Map<String, String> tokensByRole;

    @BeforeEach
    void createOneUserPerRole() {
        cleanProvisioningState();
        tokensByRole = transactions.execute(status -> {
            Map<String, String> tokens = new java.util.LinkedHashMap<>();
            for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "PLATFORM_ADMIN"}) {
                tokens.put(role, jwtService.generateAccessToken(persistPerson(role)));
            }
            return tokens;
        });
    }

    @AfterEach
    void cleanUp() {
        cleanProvisioningState();
    }

    @Test
    void everyRoleExceptPlatformAdminIsForbiddenAndAnonymousIsRejected() throws Exception {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO")) {
            provision(tokensByRole.get(role), null, "dir@example.com", "30111222").andExpect(status().isForbidden());
        }
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("dir@example.com", "30111222")))
                .andExpect(status().isForbidden());

        assertThat(count("person_role pr join role r on r.id = pr.role_id where r.name = 'DIRECTOR'")).isZero();
        assertThat(count("institution")).isZero();
        assertThat(count("platform_provisioning_log")).isZero();
    }

    @Test
    void provisioningCreatesTheInstitutionAndADirectorWithoutUsablePasswordAndAuditsIt() throws Exception {
        String response = provision(platformAdminToken(), null, "dir@example.com", "30111222")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionCreated").value(true))
                .andExpect(jsonPath("$.directorEmail").value("dir@example.com"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContainIgnoringCase("password");
        JsonNode json = objectMapper.readTree(response);
        Long directorId = json.get("directorPersonId").asLong();
        assertThat(count("institution")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from institution", String.class)).isEqualTo("Instituto Demo");
        assertThat(jdbc.queryForObject("select password from person where id = ?", String.class, directorId)).isNull();
        assertThat(count("person_role pr join role r on r.id = pr.role_id where r.name = 'DIRECTOR' and pr.person_id = " + directorId)).isEqualTo(1);
        Map<String, Object> log = jdbc.queryForMap("select * from platform_provisioning_log");
        assertThat(log.get("reason")).isEqualTo("Alta del primer instituto");
        assertThat(log.get("institution_created")).isEqualTo(true);
        assertThat(((Number) log.get("director_person_id")).longValue()).isEqualTo(directorId);
        assertThat(log.get("actor_person_id")).isNotNull();
        assertThat(log.get("occurred_at")).isNotNull();
    }

    @Test
    void theCreatedDirectorCanSetAPasswordWithTheEmailedCodeAndLogIn(CapturedOutput output) throws Exception {
        String response = provision(platformAdminToken(), null, "dir@example.com", "30111222")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long directorId = objectMapper.readTree(response).get("directorPersonId").asLong();
        String code = applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(directorId))
                .findFirst().orElseThrow().code();

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"30111222\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        String verified = mockMvc.perform(post("/auth/verify-reset-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dir@example.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resetToken = objectMapper.readTree(verified).get("resetToken").asText();
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"30111222\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        assertThat(output.getAll()).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void aSecondProvisioningWithADifferentKeyReturns409AndCreatesNothingMore() throws Exception {
        provision(platformAdminToken(), "key-1", "dir@example.com", "30111222").andExpect(status().isCreated());

        provision(platformAdminToken(), "key-2", "otro@example.com", "30111333").andExpect(status().isConflict());
        provision(platformAdminToken(), null, "otro@example.com", "30111333").andExpect(status().isConflict());

        assertThat(count("person_role pr join role r on r.id = pr.role_id where r.name = 'DIRECTOR'")).isEqualTo(1);
        assertThat(count("institution")).isEqualTo(1);
        assertThat(count("platform_provisioning_log")).isEqualTo(1);
    }

    @Test
    void retryingWithTheSameIdempotencyKeyReturnsTheSameResultWithoutDuplicates() throws Exception {
        String first = provision(platformAdminToken(), "retry-key", "dir@example.com", "30111222")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = provision(platformAdminToken(), "retry-key", "dir@example.com", "30111222")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(count("person_role pr join role r on r.id = pr.role_id where r.name = 'DIRECTOR'")).isEqualTo(1);
        assertThat(count("institution")).isEqualTo(1);
        assertThat(count("platform_provisioning_log")).isEqualTo(1);
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestReturns409() throws Exception {
        provision(platformAdminToken(), "same-key", "dir@example.com", "30111222").andExpect(status().isCreated());

        provision(platformAdminToken(), "same-key", "otro@example.com", "30111333").andExpect(status().isConflict());

        assertThat(count("platform_provisioning_log")).isEqualTo(1);
    }

    @Test
    void anExistingInstitutionIsReusedInsteadOfCreatingASecondOne() throws Exception {
        jdbc.update("insert into institution (name) values ('Instituto Preexistente')");

        provision(platformAdminToken(), null, "dir@example.com", "30111222")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionCreated").value(false));

        assertThat(count("institution")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from institution", String.class)).isEqualTo("Instituto Preexistente");
    }

    @Test
    void aTooShortReasonOrAnAlreadyRegisteredEmailIsRejectedWithoutCreatingAnything() throws Exception {
        mockMvc.perform(post(ENDPOINT).header("Authorization", "Bearer " + platformAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dir@example.com", "30111222").replace("Alta del primer instituto", "cort")))
                .andExpect(status().isBadRequest());
        jdbc.update("update person set email = 'dir@example.com' where username like 'alumno.%'");
        provision(platformAdminToken(), null, "dir@example.com", "30111222").andExpect(status().isBadRequest());

        assertThat(count("institution")).isZero();
        assertThat(count("platform_provisioning_log")).isZero();
    }

    @Test
    void twoSimultaneousRequestsWithTheSameIdempotencyKeyReturnTheSameResultAndCreateOnce() throws Exception {
        String token = platformAdminToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<String[]> sameRequest = () -> {
            ready.countDown();
            go.await();
            var result = provision(token, "race-key", "dir@example.com", "30111222").andReturn().getResponse();
            return new String[] {String.valueOf(result.getStatus()), result.getContentAsString()};
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String[]> first = executor.submit(sameRequest);
            Future<String[]> second = executor.submit(sameRequest);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            String[] firstResult = first.get(30, TimeUnit.SECONDS);
            String[] secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult[0]).isEqualTo("201");
            assertThat(secondResult[0]).isEqualTo("201");
            assertThat(secondResult[1]).isEqualTo(firstResult[1]);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("person_role pr join role r on r.id = pr.role_id where r.name = 'DIRECTOR'")).isEqualTo(1);
        assertThat(count("institution")).isEqualTo(1);
        assertThat(count("platform_provisioning_log")).isEqualTo(1);
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void statusReportsNoDirectorAndNoInstitutionBeforeProvisioning() throws Exception {
        mockMvc.perform(get(STATUS_ENDPOINT).header("Authorization", "Bearer " + platformAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directorExists").value(false))
                .andExpect(jsonPath("$.institutionName").doesNotExist());
    }

    @Test
    void statusReportsTheInstitutionWithoutDirectorWhenOnlyTheInstitutionExists() throws Exception {
        jdbc.update("insert into institution (name) values ('Instituto Preexistente')");

        mockMvc.perform(get(STATUS_ENDPOINT).header("Authorization", "Bearer " + platformAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directorExists").value(false))
                .andExpect(jsonPath("$.institutionName").value("Instituto Preexistente"));
    }

    @Test
    void statusReportsTheDirectorAndTheInstitutionNameWithoutSensitiveData() throws Exception {
        provision(platformAdminToken(), null, "dir@example.com", "30111222").andExpect(status().isCreated());

        String body = mockMvc.perform(get(STATUS_ENDPOINT).header("Authorization", "Bearer " + platformAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directorExists").value(true))
                .andExpect(jsonPath("$.institutionName").value("Instituto Demo"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).fieldNames()).toIterable().containsExactlyInAnyOrder("directorExists", "institutionName");
        assertThat(body).doesNotContain("dir@example.com").doesNotContain("30111222");
    }

    @Test
    void statusIsForbiddenForEveryRoleExceptPlatformAdmin() throws Exception {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO")) {
            mockMvc.perform(get(STATUS_ENDPOINT).header("Authorization", "Bearer " + tokensByRole.get(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(STATUS_ENDPOINT)).andExpect(status().isForbidden());
    }

    private String platformAdminToken() {
        return tokensByRole.get("PLATFORM_ADMIN");
    }

    private ResultActions provision(String token, String idempotencyKey, String email, String documentNumber) throws Exception {
        var request = post(ENDPOINT).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(email, documentNumber));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private String body(String email, String documentNumber) {
        return "{\"institutionName\":\"Instituto Demo\",\"firstName\":\"Dora\",\"lastName\":\"Directora\",\"email\":\"" + email
                + "\",\"documentType\":\"DNI\",\"documentNumber\":\"" + documentNumber + "\",\"reason\":\"Alta del primer instituto\"}";
    }

    private long count(String fromAndWhere) {
        return jdbc.queryForObject("select count(*) from " + fromAndWhere, Long.class);
    }

    private void cleanProvisioningState() {
        jdbc.update("delete from platform_provisioning_log");
        jdbc.update("delete from person where username = '30111222' or email = 'dir@example.com' or username ~ '^(alumno|docente|preceptor|administrativo|platform_admin)[.][0-9a-f-]{36}$'");
        jdbc.update("delete from institution");
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername(roleName.toLowerCase() + "." + suffix);
        person.setEmail(roleName.toLowerCase() + "." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
