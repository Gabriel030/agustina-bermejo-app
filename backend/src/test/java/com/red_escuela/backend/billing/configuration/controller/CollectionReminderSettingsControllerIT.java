package com.red_escuela.backend.billing.configuration.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CollectionReminderSettingsControllerIT extends AbstractIntegrationTest {
    private static final String BASE = "/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS";
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 3);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
    }

    private String tokenWithRole(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = roleName.toLowerCase() + "." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private static String body(int dailyCap, int postWithdrawalMonths, String start, String end, LocalDate effectiveFrom) {
        return "{\"values\":{\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":" + postWithdrawalMonths + ","
                + "\"minDaysBetweenEmails\":3,\"maxReminderEmailsPerMonth\":4,\"emailWindowStart\":\"" + start + "\",\"emailWindowEnd\":\"" + end
                + "\",\"dailyEmailCap\":" + dailyCap + ",\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2},\"effectiveFrom\":\"" + effectiveFrom
                + "\",\"changeReason\":\"motivo\"}";
    }

    private ResultActions postTo(String path, String token, String content) throws Exception {
        return mockMvc.perform(post(BASE + path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    @Test
    @Tag("D-351")
    @Tag("D-114")
    void administrativeProposalAnswers202WithTheAuthorizationRequest() throws Exception {
        postTo("/changes", tokenWithRole("ADMINISTRATIVO"), body(280, 1, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.authorizationRequestId").isNumber());
    }

    @Test
    @Tag("D-351")
    @Tag("D-095")
    void directorPublicationAnswers201WithTheScheduledVersionAndItAppearsInTheHistory() throws Exception {
        String token = tokenWithRole("DIRECTOR");

        postTo("/changes", token, body(280, 1, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.values.dailyEmailCap").value(280))
                .andExpect(jsonPath("$.directPublication").value(true));

        mockMvc.perform(get(BASE + "/versions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].values.emailWindowStart").value("09:00:00"));
    }

    @Test
    @Tag("D-350")
    void sameDayVigencyAnswers400WithTheDeclaredCode() throws Exception {
        postTo("/changes", tokenWithRole("ADMINISTRATIVO"), body(280, 1, "09:00", "11:00", TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EFFECTIVE_FROM_BEFORE_NEXT_DAY"))
                .andExpect(jsonPath("$.violations[0].code").value("EFFECTIVE_FROM_BEFORE_NEXT_DAY"));
    }

    @Test
    @Tag("D-351")
    void incoherentValuesAnswer400WithViolations() throws Exception {
        String token = tokenWithRole("ADMINISTRATIVO");

        postTo("/changes", token, body(280, 0, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_REMINDER_SETTINGS"))
                .andExpect(jsonPath("$.violations[0].message").value("Los meses de resumen posterior a la baja deben estar entre 1 y 24."));
        postTo("/changes", token, body(280, 1, "11:00", "09:00", TODAY.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_REMINDER_SETTINGS"));
        postTo("/changes", token, body(0, 1, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_REMINDER_SETTINGS"));
    }

    @Test
    @Tag("D-351")
    void validationEndpointReportsViolationsWithoutPersistingAnything() throws Exception {
        String token = tokenWithRole("ADMINISTRATIVO");

        postTo("/validation", token, body(280, 61, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations[0].code").value("INVALID_COLLECTION_REMINDER_SETTINGS"));
        postTo("/validation", token, body(280, 1, "09:00", "11:00", TODAY.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations", hasSize(0)));
        mockMvc.perform(get(BASE + "/versions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Tag("D-116")
    void secondOpenProposalAnswers409() throws Exception {
        String token = tokenWithRole("ADMINISTRATIVO");
        postTo("/changes", token, body(280, 1, "09:00", "11:00", TODAY.plusDays(1))).andExpect(status().isAccepted());

        postTo("/changes", token, body(300, 1, "09:00", "11:00", TODAY.plusDays(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_ALREADY_OPEN"));
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-110")
    @ValueSource(strings = {"PRECEPTOR", "ALUMNO", "DOCENTE"})
    void rolesWithoutBillingAccessAreForbiddenOnEveryEndpoint(String role) throws Exception {
        String token = tokenWithRole(role);

        postTo("/changes", token, body(280, 1, "09:00", "11:00", TODAY.plusDays(1))).andExpect(status().isForbidden());
        postTo("/validation", token, body(280, 1, "09:00", "11:00", TODAY.plusDays(1))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/versions").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-110")
    void anonymousIsForbidden() throws Exception {
        mockMvc.perform(get(BASE + "/versions")).andExpect(status().isForbidden());
    }
}
