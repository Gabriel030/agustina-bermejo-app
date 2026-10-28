package com.red_escuela.backend.billing.collections.controller;

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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CollectionReminderImpactControllerIT extends AbstractIntegrationTest {
    private static final String PATH = "/collection-reminders/impact";
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 2);

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

    private static String notice(String key, String type, String milestone, int days, String channels) {
        return "{\"noticeKey\":\"" + key + "\",\"messageType\":\"" + type + "\",\"milestone\":\"" + milestone + "\",\"offsetDays\":" + days + ",\"channels\":" + channels + "}";
    }

    private static String standardNotices(int beforeDueDays) {
        return "[" + notice("issuance", "ISSUANCE_NOTICE", "ISSUANCE", 0, "[\"IN_APP\"]") + ","
                + notice("after-issue", "AFTER_ISSUE_REMINDER", "ISSUANCE", 5, "[\"IN_APP\",\"EMAIL\"]") + ","
                + notice("before-due", "BEFORE_DUE_REMINDER", "DUE_DATE", beforeDueDays, "[\"IN_APP\",\"EMAIL\"]") + ","
                + notice("after-due", "AFTER_DUE_REMINDER", "DUE_DATE", 10, "[\"IN_APP\",\"EMAIL\"]") + "]";
    }

    private static String body(String group, String notices, LocalDate effectiveFrom) {
        return "{\"group\":" + group + ",\"schedule\":{\"notices\":" + notices + "},\"effectiveFrom\":\"" + effectiveFrom + "\"}";
    }

    private ResultActions postImpact(String token, String content) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-110")
    @Tag("D-350")
    @ValueSource(strings = {"ADMINISTRATIVO", "DIRECTOR"})
    void administrativeAndDirectorGetTheImpactCount(String role) throws Exception {
        postImpact(tokenWithRole(role), body("\"PROGRAMS\"", standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithChangedSchedule").isNumber());
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-110")
    @ValueSource(strings = {"PRECEPTOR", "ALUMNO", "DOCENTE"})
    void rolesWithoutBillingAccessAreForbidden(String role) throws Exception {
        postImpact(tokenWithRole(role), body("\"PROGRAMS\"", standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-110")
    void anonymousIsForbidden() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("\"PROGRAMS\"", standardNotices(-2), TODAY.plusDays(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-368")
    void brokenNoticesComeBackAsViolationsAndNothingIsSimulated() throws Exception {
        String broken = "[" + notice("a", "AFTER_DUE_REMINDER", "DUE_DATE", 61, "[\"EMAIL\"]") + "]";

        postImpact(tokenWithRole("DIRECTOR"), body("\"PROGRAMS\"", broken, TODAY.plusDays(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_REMINDER_SCHEDULE"))
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("notices[0].offsetDays")));
    }

    @Test
    @Tag("D-368")
    void aProposalThatFollowsTheGroupIsRefused() throws Exception {
        String content = "{\"group\":\"PROGRAMS\",\"schedule\":{\"followsGroupSchedule\":true,\"notices\":[]},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}";

        postImpact(tokenWithRole("DIRECTOR"), content).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("followsGroupSchedule: usá /removal")));
    }

    @Test
    @Tag("D-368")
    void brokenGlobalSettingsAreRefused() throws Exception {
        String content = "{\"group\":\"PROGRAMS\",\"schedule\":{\"notices\":" + standardNotices(-1) + "},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\","
                + "\"globalSettings\":{\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":6,\"emailWindowStart\":\"11:00:00\",\"emailWindowEnd\":\"09:00:00\","
                + "\"dailyEmailCap\":250,\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2}}";

        postImpact(tokenWithRole("DIRECTOR"), content).andExpect(status().isBadRequest());
    }

    @Test
    @Tag("D-368")
    void missingFieldsAndUnknownTargetsAreRefused() throws Exception {
        String director = tokenWithRole("DIRECTOR");

        postImpact(director, "{\"schedule\":{\"notices\":" + standardNotices(-1) + "},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}").andExpect(status().isBadRequest());
        postImpact(director, "{\"group\":\"PROGRAMS\",\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}").andExpect(status().isBadRequest());
        postImpact(director, "{\"group\":\"PROGRAMS\",\"schedule\":{\"notices\":" + standardNotices(-1) + "}}").andExpect(status().isBadRequest());
        postImpact(director, "{\"group\":\"PROGRAMS\",\"targetId\":999999999,\"schedule\":{\"notices\":" + standardNotices(-1) + "},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}")
                .andExpect(status().isNotFound());
    }
}
