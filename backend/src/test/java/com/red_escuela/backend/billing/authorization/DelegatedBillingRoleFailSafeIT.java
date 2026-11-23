package com.red_escuela.backend.billing.authorization;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("D-217")
@Tag("D-176")
class DelegatedBillingRoleFailSafeIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);
    private static final String DEFINITIONS = "/discount-definitions";
    private static final String MANAGE_DEFINITION = "billing.manage_discount_definition";
    private static final String DECIDE_REQUEST = "billing.decide_authorization_request";

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
    @Autowired
    private PermissionSnapshotCache cache;

    private Person director;
    private Person administrative;
    private Person preceptor;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        director = persistPerson("failsafe.director", "DIRECTOR");
        administrative = persistPerson("failsafe.administrative", "ADMINISTRATIVO");
        preceptor = persistPerson("failsafe.preceptor", "PRECEPTOR");
    }

    @AfterEach
    void tearDown() {
        String people = director.getId() + "," + administrative.getId() + "," + preceptor.getId();
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        try {
            jdbc.update("delete from discount_definition_status_change where discount_definition_id in (select id from discount_definition where created_by_id in (" + people + "))");
            jdbc.update("delete from discount_definition where created_by_id in (" + people + ")");
        } finally {
            jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
        }
        jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id in (" + people + "))");
        jdbc.update("delete from authorization_request where requested_by_id in (" + people + ")");
        jdbc.update("delete from permission_module_mode where module = 'billing'");
        jdbc.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where code in (?, ?, 'announcement.read', 'billing.run_billing'))", MANAGE_DEFINITION, DECIDE_REQUEST);
        cache.invalidate();
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

    private void useMode(String mode) {
        if ("ENFORCE".equals(mode)) {
            for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
                jdbc.update("insert into permission_module_mode (role, module, mode) values (?, 'billing', 'ENFORCE')", role);
            }
        }
        cache.invalidate();
    }

    private void delegateToPreceptor(String code) {
        jdbc.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = ?", code);
        cache.invalidate();
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
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + jwtService.generateAccessToken(caller)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long definitionsNamed(String name) {
        return jdbc.queryForObject("select count(*) from discount_definition where name = ?", Long.class, name);
    }

    private Long pendingRequestIdOf(ResultActions response) throws Exception {
        return objectMapper.readTree(response.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).get("authorizationRequestId").asLong();
    }

    private String statusOfRequest(Long requestId) {
        return jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId);
    }

    private void assertEveryRoleBehavesFailSafe(String mode) throws Exception {
        useMode(mode);
        delegateToPreceptor(MANAGE_DEFINITION);
        delegateToPreceptor(DECIDE_REQUEST);

        postJson(DEFINITIONS, director, annualUpfront(mode + " director")).andExpect(status().isCreated());
        assertThat(definitionsNamed(mode + " director")).isEqualTo(1L);

        Long administrativeRequestId = pendingRequestIdOf(postJson(DEFINITIONS, administrative, annualUpfront(mode + " administrativo")));
        assertThat(definitionsNamed(mode + " administrativo")).isZero();

        if ("ENFORCE".equals(mode)) {
            Long delegatedRequestId = pendingRequestIdOf(postJson(DEFINITIONS, preceptor, annualUpfront(mode + " delegado")));
            assertThat(definitionsNamed(mode + " delegado")).isZero();
            assertThat(statusOfRequest(delegatedRequestId)).isEqualTo("PENDING");
        } else {
            postJson(DEFINITIONS, preceptor, annualUpfront(mode + " delegado")).andExpect(status().isForbidden());
        }

        Map<String, Object> decision = Map.of("decisionReason", "de acuerdo");
        postJson("/authorization-requests/" + administrativeRequestId + "/approve", administrative, decision).andExpect(status().isForbidden());
        postJson("/authorization-requests/" + administrativeRequestId + "/approve", preceptor, decision).andExpect(status().isForbidden());
        assertThat(statusOfRequest(administrativeRequestId)).isEqualTo("PENDING");
        assertThat(definitionsNamed(mode + " administrativo")).isZero();

        postJson("/authorization-requests/" + administrativeRequestId + "/approve", director, decision).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(definitionsNamed(mode + " administrativo")).isEqualTo(1L);
    }

    @Test
    void inLegacyOnlyTheDirectorAppliesAndEveryoneElseLeavesPendingRequestsOrIsDenied() throws Exception {
        assertEveryRoleBehavesFailSafe("LEGACY");
    }

    @Test
    void inEnforceADelegatedRoleActsLikeAnAdministrativeAndNeverAppliesDirectly() throws Exception {
        assertEveryRoleBehavesFailSafe("ENFORCE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "billing.decide_authorization_request", "billing.run_billing", "billing.issue_monthly_invoices_manually", "billing.manage_promotion",
            "billing.cancel_price_version", "billing.manage_payment_plan_options", "billing.manage_year_billing_template",
            "billing.set_late_payment_settings", "billing.manage_payment_gateway", "billing.publish_annual_upfront_terms", "billing.revoke_payment_approval"})
    void permissionsWithoutAnApprovalFlowAreFixedToTheDirectorAndTheGridRefusesToDelegateThem(String code) throws Exception {
        assertThat(jdbc.queryForObject("select fixed_to_director from permission where code = ?", Boolean.class, code)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from role_permission where role <> 'DIRECTOR' and permission_id = (select id from permission where code = ?)", Long.class, code)).isZero();

        Map<String, Object> change = Map.of("role", "PRECEPTOR", "permissionCode", code, "granted", true);
        Map<String, Object> request = Map.of("reason", "Delegar la decisión", "sensitiveConfirmed", true, "changes", List.of(change));
        mockMvc.perform(put("/permissions/grid").header("Authorization", "Bearer " + jwtService.generateAccessToken(director)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FIXED_TO_DIRECTOR_PERMISSION_NOT_EDITABLE"));

        assertThat(jdbc.queryForObject("select count(*) from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = ?)", Long.class, code)).isZero();
        String catalog = mockMvc.perform(get("/permissions/catalog").header("Authorization", "Bearer " + jwtService.generateAccessToken(director)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean markedAsFixed = objectMapper.readTree(catalog).get("modules").valueStream()
                .flatMap(module -> module.get("permissions").valueStream())
                .anyMatch(permission -> code.equals(permission.get("code").asString()) && permission.get("fixedToDirector").asBoolean());
        assertThat(markedAsFixed).isTrue();
    }

    private ResultActions putGrid(List<Map<String, Object>> changes) throws Exception {
        Map<String, Object> request = Map.of("reason", "Ajuste de permisos", "sensitiveConfirmed", true, "changes", changes);
        return mockMvc.perform(put("/permissions/grid").header("Authorization", "Bearer " + jwtService.generateAccessToken(director)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private long preceptorRowsOf(String code) {
        return jdbc.queryForObject("select count(*) from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = ?)", Long.class, code);
    }

    @Test
    void revokingAFixedToDirectorPermissionIsAllowedAndRemovesAStaleGrant() throws Exception {
        putGrid(List.of(Map.of("role", "PRECEPTOR", "permissionCode", "billing.run_billing", "granted", false))).andExpect(status().isOk());
        assertThat(preceptorRowsOf("billing.run_billing")).isZero();

        delegateToPreceptor("billing.run_billing");
        putGrid(List.of(Map.of("role", "PRECEPTOR", "permissionCode", "billing.run_billing", "granted", false))).andExpect(status().isOk());
        assertThat(preceptorRowsOf("billing.run_billing")).isZero();
    }

    @Test
    void aMixedChangeWithAValidGrantAndTheRevocationOfAFixedPermissionIsApplied() throws Exception {
        delegateToPreceptor("billing.run_billing");
        putGrid(List.of(
                Map.of("role", "PRECEPTOR", "permissionCode", "announcement.read", "granted", true),
                Map.of("role", "PRECEPTOR", "permissionCode", "billing.run_billing", "granted", false))).andExpect(status().isOk());
        assertThat(preceptorRowsOf("announcement.read")).isEqualTo(1L);
        assertThat(preceptorRowsOf("billing.run_billing")).isZero();
    }
}
