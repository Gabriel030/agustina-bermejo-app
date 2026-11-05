package com.red_escuela.backend.billing.configuration.controller;

import com.jayway.jsonpath.JsonPath;
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
@Tag("D-216")
@Tag("D-351")
class BillingConfigurationChangeDescriptionIT extends AbstractIntegrationTest {
    private static final String SETTINGS = "/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS";
    private static final String SCHEDULES = "/billing-configuration/collection-reminder-schedules";
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

    private static String settingsBody(int dailyCap, LocalDate effectiveFrom) {
        return "{\"values\":{\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":6,\"emailWindowStart\":\"09:00\",\"emailWindowEnd\":\"11:00\",\"dailyEmailCap\":" + dailyCap
                + ",\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"motivo\"}";
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

    private static String scheduleBody(int beforeDueDays, LocalDate effectiveFrom) {
        return "{\"schedule\":{\"notices\":" + standardNotices(beforeDueDays) + "},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"motivo\"}";
    }

    private ResultActions postTo(String path, String token, String content) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ResultActions detailOf(String requestId, String token) throws Exception {
        return mockMvc.perform(get("/authorization-requests/" + requestId).header("Authorization", "Bearer " + token));
    }

    private String proposalIdOf(ResultActions proposal) throws Exception {
        return JsonPath.read(proposal.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.authorizationRequestId").toString();
    }

    @Test
    void theDetailOfAnAdministrativeSettingsProposalComparesTheValueInForceTodayWithTheProposedOne() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String owner = tokenWithRole("ADMINISTRATIVO");
        String other = tokenWithRole("ADMINISTRATIVO");
        postTo(SETTINGS + "/changes", director, settingsBody(260, TODAY.plusDays(1))).andExpect(status().isCreated());
        MutableClock.INSTANCE.setDate(TODAY.plusDays(1));
        String requestId = proposalIdOf(postTo(SETTINGS + "/changes", owner, settingsBody(300, TODAY.plusDays(2))));

        for (String token : new String[]{director, owner}) {
            detailOf(requestId, token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.operationType").value("CHANGE_BILLING_CONFIGURATION"))
                    .andExpect(jsonPath("$.detailBlock.type").value("CHANGE"))
                    .andExpect(jsonPath("$.detailBlock.changeRows", hasSize(8)))
                    .andExpect(jsonPath("$.detailBlock.changeRows[4].label").value("Tope diario de mails"))
                    .andExpect(jsonPath("$.detailBlock.changeRows[4].today").value("260 mails"))
                    .andExpect(jsonPath("$.detailBlock.changeRows[4].proposed").value("300 mails"))
                    .andExpect(jsonPath("$.detailBlock.changeRows[4].difference").value("+40 mails"))
                    .andExpect(jsonPath("$.detailBlock.changeRows[2].difference").value("Sin cambio"))
                    .andExpect(jsonPath("$.detailBlock.refundParts", hasSize(0)));
        }
        detailOf(requestId, other).andExpect(status().isForbidden());
    }

    @Test
    void theDetailOfAScheduleProposalComparesTheResolvedListInForceWithTheProposedNotices() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String owner = tokenWithRole("ADMINISTRATIVO");
        String requestId = proposalIdOf(postTo(SCHEDULES + "/PROGRAMS/changes", owner, scheduleBody(-2, TODAY.plusDays(1))));

        detailOf(requestId, director).andExpect(status().isOk())
                .andExpect(jsonPath("$.detailBlock.type").value("CHANGE"))
                .andExpect(jsonPath("$.detailBlock.changeRows", hasSize(2)))
                .andExpect(jsonPath("$.detailBlock.changeRows[1].label").value("Aviso previo a la suspensión"))
                .andExpect(jsonPath("$.detailBlock.changeRows[1].proposed").value("—"))
                .andExpect(jsonPath("$.detailBlock.changeRows[1].difference").value("Se quita"))
                .andExpect(jsonPath("$.detailBlock.changeRows[0].label").value("Recordatorio previo al vencimiento"))
                .andExpect(jsonPath("$.detailBlock.changeRows[0].today").value("1 día antes del vencimiento · App y Mail"))
                .andExpect(jsonPath("$.detailBlock.changeRows[0].proposed").value("2 días antes del vencimiento · App y Mail"))
                .andExpect(jsonPath("$.detailBlock.changeRows[0].difference").value("Cambia"));
    }

    @Test
    void onceDecidedTheRequestNoLongerCarriesTheTableNorAnEffect() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String owner = tokenWithRole("ADMINISTRATIVO");
        String requestId = proposalIdOf(postTo(SETTINGS + "/changes", owner, settingsBody(300, TODAY.plusDays(1))));
        detailOf(requestId, owner).andExpect(jsonPath("$.detailBlock.type").value("CHANGE")).andExpect(jsonPath("$.effectLines", hasSize(1)));

        mockMvc.perform(post("/authorization-requests/" + requestId + "/reject").header("Authorization", "Bearer " + director).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionReason\":\"No corresponde\"}")).andExpect(status().isOk());

        detailOf(requestId, owner).andExpect(status().isOk()).andExpect(jsonPath("$.request.status").value("REJECTED")).andExpect(jsonPath("$.detailBlock.type").value("NONE"))
                .andExpect(jsonPath("$.detailBlock.changeRows", hasSize(0))).andExpect(jsonPath("$.effectLines", hasSize(0)));
    }
}
