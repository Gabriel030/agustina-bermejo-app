package com.red_escuela.backend.billing.configuration.controller;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleSource;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CollectionReminderScheduleControllerIT extends AbstractIntegrationTest {
    private static final String BASE = "/billing-configuration/collection-reminder-schedules";
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 3);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BillingConfigurationReader reader;

    private Long nursingId;
    private Long radiologyId;
    private Long firstAidCourseId;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        nursingId = insertProgram("Enfermería");
        radiologyId = insertProgram("Radiología");
        jdbcTemplate.update("insert into term (year_number, status, start_date, end_date) values (2027, true, timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00')");
        Long termId = jdbcTemplate.queryForObject("select max(id) from term", Long.class);
        firstAidCourseId = jdbcTemplate.queryForObject("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS') returning id", Long.class, termId);
    }

    private Long insertProgram(String name) {
        return jdbcTemplate.queryForObject("insert into program (name, status, total_years, currency) values (?, true, 3, 'ARS') returning id", Long.class,
                name + "." + System.nanoTime());
    }

    private String tokenWithRole(String roleName) {
        return tokenOf(roleName, "Persona", "Prueba");
    }

    private String tokenOf(String roleName, String name, String lastname) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = roleName.toLowerCase() + "." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName(name);
        person.setLastname(lastname);
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

    private static String body(String notices, LocalDate effectiveFrom) {
        return "{\"schedule\":{\"notices\":" + notices + "},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"motivo\"}";
    }

    private ResultActions postTo(String path, String token, String content) throws Exception {
        return mockMvc.perform(post(BASE + path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ResultActions getFrom(String path, String token) throws Exception {
        return mockMvc.perform(get(BASE + path).header("Authorization", "Bearer " + token));
    }

    private int beforeDueDaysFor(TeachingGroup group, Long targetId, LocalDate date) {
        return reader.reminderScheduleFor(new ReminderScheduleTarget(group, targetId), date).notices().stream()
                .filter(notice -> notice.noticeKey().equals("before-due")).findFirst().orElseThrow().offsetDays();
    }

    @Test
    @Tag("D-351")
    @Tag("D-368")
    void theVersionsNameWhoProposedAndWhoDecidedBesideTheirIds() throws Exception {
        String administrative = tokenOf("ADMINISTRATIVO", "Ana", "Proponente");
        String director = tokenOf("DIRECTOR", "Dario", "Decisor");

        postTo("/PROGRAMS/changes", administrative, body(standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isAccepted());
        getFrom("/PROGRAMS/versions", director).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proposedById").isNumber())
                .andExpect(jsonPath("$[0].proposedByName").value("Ana Proponente"))
                .andExpect(jsonPath("$[0].decidedById").value(nullValue()))
                .andExpect(jsonPath("$[0].decidedByName").value(nullValue()));
        postTo("/MONTHLY_COURSES/changes", director, body(standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposedByName").value("Dario Decisor"))
                .andExpect(jsonPath("$.decidedByName").value("Dario Decisor"));
        getFrom("/MONTHLY_COURSES/versions", director).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proposedByName").value("Dario Decisor"))
                .andExpect(jsonPath("$[0].decidedByName").value("Dario Decisor"));
    }

    @Test
    @Tag("D-368")
    @Tag("D-351")
    @Tag("D-117")
    void administrativeProposalStaysPendingWhileTheCurrentListKeepsRulingAndAnAdministrativeCannotApproveIt() throws Exception {
        String administrative = tokenWithRole("ADMINISTRATIVO");
        String director = tokenWithRole("DIRECTOR");

        MvcResult proposal = postTo("/PROGRAMS/changes", administrative, body(standardNotices(-2), TODAY.plusDays(1)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.authorizationRequestId").isNumber()).andReturn();

        getFrom("/PROGRAMS/versions", director).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PROPOSED"));
        assertThat(beforeDueDaysFor(TeachingGroup.PROGRAMS, null, TODAY.plusDays(1))).isEqualTo(-1);

        String requestId = JsonPath.read(proposal.getResponse().getContentAsString(), "$.authorizationRequestId").toString();
        mockMvc.perform(post("/authorization-requests/" + requestId + "/approve").header("Authorization", "Bearer " + administrative)).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-368")
    @Tag("D-350")
    @Tag("D-095")
    void directorPublishesForTomorrowButNotForTodayAndTheListIsStoredInTimelineOrder() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String unordered = "[" + notice("after-due", "AFTER_DUE_REMINDER", "DUE_DATE", 10, "[\"EMAIL\"]") + ","
                + notice("before-suspension", "BEFORE_SUSPENSION_REMINDER", "SUSPENSION", -1, "[\"EMAIL\",\"IN_APP\"]") + ","
                + notice("after-issue", "AFTER_ISSUE_REMINDER", "ISSUANCE", 5, "[\"IN_APP\"]") + "]";

        postTo("/MONTHLY_COURSES/changes", director, body(unordered, TODAY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EFFECTIVE_FROM_BEFORE_NEXT_DAY"));
        postTo("/MONTHLY_COURSES/changes", director, body(unordered, TODAY.plusDays(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.schedule.notices[0].noticeKey").value("after-issue"))
                .andExpect(jsonPath("$.schedule.notices[1].noticeKey").value("after-due"))
                .andExpect(jsonPath("$.schedule.notices[2].noticeKey").value("before-suspension"))
                .andExpect(jsonPath("$.schedule.notices[2].channels[0]").value("IN_APP"));
        assertThat(jdbcTemplate.queryForObject("select config_values -> 'schedule' -> 'notices' -> 0 ->> 'noticeKey' from configuration_version where scope = 'COURSE_REMINDER_SCHEDULE'",
                String.class)).isEqualTo("after-issue");
    }

    @Test
    @Tag("D-368")
    void everyBrokenRuleComesBackAsItsOwnViolationAndAMissingRepetitionLimitIsOneOfThem() throws Exception {
        String administrative = tokenWithRole("ADMINISTRATIVO");
        String broken = "[" + notice("a", "AFTER_DUE_REMINDER", "DUE_DATE", 61, "[\"EMAIL\"]") + ","
                + notice("b", "AFTER_DUE_REMINDER", "DUE_DATE", 5, "[]") + ","
                + "{\"noticeKey\":\"c\",\"messageType\":\"AFTER_DUE_REMINDER\",\"milestone\":\"DUE_DATE\",\"offsetDays\":9,\"channels\":[\"EMAIL\"],\"repetition\":{\"everyDays\":3}}]";

        postTo("/PROGRAMS/changes", administrative, body(broken, TODAY.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_REMINDER_SCHEDULE"))
                .andExpect(jsonPath("$.violations", hasSize(3)))
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("notices[0].offsetDays")))
                .andExpect(jsonPath("$.violations[1].message").value(startsWith("notices[1].channels")))
                .andExpect(jsonPath("$.violations[2].message").value(startsWith("notices[2].repetition.until")));
        postTo("/PROGRAMS/validation", administrative, body(broken, TODAY.plusDays(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.violations", hasSize(3)));
        postTo("/PROGRAMS/validation", administrative, body(standardNotices(-2), TODAY.plusDays(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.violations", hasSize(0)));
        getFrom("/PROGRAMS/versions", administrative).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Tag("D-116")
    void aSecondOpenProposalOnTheSameExceptionIsRefusedButOtherTopicsStayFree() throws Exception {
        String administrative = tokenWithRole("ADMINISTRATIVO");
        String path = "/PROGRAMS/exceptions/" + nursingId + "/changes";
        postTo(path, administrative, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isAccepted());

        postTo(path, administrative, body(standardNotices(-2), TODAY.plusDays(2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_ALREADY_OPEN"));
        postTo("/PROGRAMS/changes", administrative, body(standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isAccepted());
        postTo("/MONTHLY_COURSES/changes", administrative, body(standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isAccepted());
        postTo("/PROGRAMS/exceptions/" + radiologyId + "/changes", administrative, body(standardNotices(-3), TODAY.plusDays(1))).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-116")
    void removalWithAnOpenProposalOnTheExceptionAnswers409() throws Exception {
        String administrative = tokenWithRole("ADMINISTRATIVO");
        postTo("/PROGRAMS/exceptions/" + nursingId + "/changes", administrative, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isAccepted());

        postTo("/PROGRAMS/exceptions/" + nursingId + "/removal", administrative, "{\"effectiveFrom\":\"" + TODAY.plusDays(2) + "\",\"changeReason\":\"quitar\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_ALREADY_OPEN"));
    }

    @Test
    @Tag("D-368")
    void changesRejectAClientThatAsksToFollowTheGroupAndPointToRemoval() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String content = "{\"schedule\":{\"followsGroupSchedule\":true,\"notices\":[]},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\",\"changeReason\":\"motivo\"}";

        postTo("/PROGRAMS/exceptions/" + nursingId + "/changes", director, content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].message").value(startsWith("followsGroupSchedule: usá /removal")));
        postTo("/PROGRAMS/exceptions/" + nursingId + "/validation", director, content).andExpect(status().isBadRequest());
    }

    @Test
    @Tag("D-368")
    void aNewExceptionIsListedInThePolicySummaryWithItsNoticeCount() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        getFrom("", director).andExpect(status().isOk()).andExpect(jsonPath("$.exceptions", hasSize(0)));

        postTo("/PROGRAMS/exceptions/" + radiologyId + "/changes", director, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isCreated());
        MutableClock.INSTANCE.setDate(TODAY.plusDays(1));

        getFrom("", director).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(2)))
                .andExpect(jsonPath("$.exceptions", hasSize(1)))
                .andExpect(jsonPath("$.exceptions[0].group").value("PROGRAMS"))
                .andExpect(jsonPath("$.exceptions[0].targetId").value(radiologyId))
                .andExpect(jsonPath("$.exceptions[0].noticeCount").value(4))
                .andExpect(jsonPath("$.exceptions[0].hasOpenProposal").value(false))
                .andExpect(jsonPath("$.exceptions[0].followsGroupFrom").value(nullValue()));
    }

    @Test
    @Tag("D-368")
    @Tag("D-350")
    void removingAnExceptionKeepsItRulingUntilTheRemovalDateAndTheSummaryAnnouncesIt() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        postTo("/PROGRAMS/changes", director, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isCreated());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/changes", director, body(standardNotices(-3), TODAY.plusDays(1))).andExpect(status().isCreated());
        postTo("/PROGRAMS/exceptions/" + radiologyId + "/changes", director, body(standardNotices(-2), TODAY.plusDays(1))).andExpect(status().isCreated());
        MutableClock.INSTANCE.setDate(TODAY.plusDays(1));
        LocalDate removalDate = TODAY.plusDays(7);

        postTo("/PROGRAMS/exceptions/" + nursingId + "/removal", director, "{\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\",\"changeReason\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EFFECTIVE_FROM_BEFORE_NEXT_DAY"));
        postTo("/PROGRAMS/exceptions/" + nursingId + "/removal", director, "{\"effectiveFrom\":\"" + removalDate + "\",\"changeReason\":\"ya no aplica\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.schedule.followsGroupSchedule").value(true));

        assertThat(reader.reminderScheduleFor(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, nursingId), removalDate.minusDays(1)).source()).isEqualTo(ReminderScheduleSource.EXCEPTION);
        assertThat(reader.reminderScheduleFor(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, nursingId), removalDate).source()).isEqualTo(ReminderScheduleSource.GROUP);
        getFrom("/PROGRAMS/exceptions/" + nursingId + "/versions", director).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].schedule.followsGroupSchedule").value(true));
        getFrom("", director).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(2)))
                .andExpect(jsonPath("$.groups[?(@.group == 'PROGRAMS')].noticeCount").value(4))
                .andExpect(jsonPath("$.exceptions", hasSize(2)))
                .andExpect(jsonPath("$.exceptions[?(@.targetId == " + nursingId + ")].followsGroupFrom").value(removalDate.toString()))
                .andExpect(jsonPath("$.exceptions[?(@.targetId == " + radiologyId + ")].followsGroupFrom").value(hasItem(nullValue())));
    }

    @Test
    @Tag("D-368")
    void unknownTargetsAnswer404AndAProgramIdIsNotAcceptedAsACourse() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        Long unknownId = 987654321L;

        postTo("/PROGRAMS/exceptions/" + unknownId + "/changes", director, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isNotFound());
        postTo("/MONTHLY_COURSES/exceptions/" + unknownId + "/changes", director, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isNotFound());
        postTo("/MONTHLY_COURSES/exceptions/" + unknownId + "/removal", director, "{\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}").andExpect(status().isNotFound());
        getFrom("/PROGRAMS/exceptions/" + unknownId + "/versions", director).andExpect(status().isNotFound());
        postTo("/MONTHLY_COURSES/exceptions/" + firstAidCourseId + "/changes", director, body(standardNotices(-1), TODAY.plusDays(1))).andExpect(status().isCreated());
    }

    @Test
    @Tag("D-351")
    @Tag("D-369")
    void theGlobalSettingsIgnoreTheRetiredCadenceFields() throws Exception {
        String administrative = tokenWithRole("ADMINISTRATIVO");
        String content = "{\"values\":{\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":6,\"minDaysBetweenEmails\":3,\"maxReminderEmailsPerMonth\":4,"
                + "\"emailWindowStart\":\"09:00\",\"emailWindowEnd\":\"11:00\",\"dailyEmailCap\":280,\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2,"
                + "\"daysBeforeDueReminder\":2},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\",\"changeReason\":\"motivo\"}";

        mockMvc.perform(post("/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/changes").header("Authorization", "Bearer " + administrative)
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-391")
    void oldRequestsWithTheRetiredCapsInSendingOverridesAreAcceptedAndNothingIsStored() throws Exception {
        String director = tokenWithRole("DIRECTOR");
        String content = "{\"schedule\":{\"notices\":" + standardNotices(-1) + ",\"sendingOverrides\":{\"dailyEmailCap\":200,\"minDaysBetweenEmails\":3,"
                + "\"maxReminderEmailsPerMonth\":4}},\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\",\"changeReason\":\"motivo\"}";

        postTo("/PROGRAMS/validation", director, content).andExpect(status().is2xxSuccessful());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/validation", director, content).andExpect(status().is2xxSuccessful());
        postTo("/PROGRAMS/changes", director, content).andExpect(status().isCreated());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/changes", director, content).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select count(*) from configuration_version where scope = 'PROGRAM_REMINDER_SCHEDULE' and topic_key <> scope", Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject("select count(*) from configuration_version where config_values::text like '%minDaysBetweenEmails%' "
                + "or config_values::text like '%maxReminderEmailsPerMonth%'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from configuration_version where config_values::text like '%\"dailyEmailCap\": 200%'", Long.class)).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-110")
    @ValueSource(strings = {"PRECEPTOR", "ALUMNO", "DOCENTE"})
    void rolesWithoutBillingAccessAreForbiddenOnEveryEndpoint(String role) throws Exception {
        String token = tokenWithRole(role);
        String content = body(standardNotices(-1), TODAY.plusDays(1));

        getFrom("", token).andExpect(status().isForbidden());
        postTo("/PROGRAMS/changes", token, content).andExpect(status().isForbidden());
        postTo("/PROGRAMS/validation", token, content).andExpect(status().isForbidden());
        getFrom("/PROGRAMS/versions", token).andExpect(status().isForbidden());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/changes", token, content).andExpect(status().isForbidden());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/validation", token, content).andExpect(status().isForbidden());
        getFrom("/PROGRAMS/exceptions/" + nursingId + "/versions", token).andExpect(status().isForbidden());
        postTo("/PROGRAMS/exceptions/" + nursingId + "/removal", token, "{\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}").andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-110")
    void anonymousIsForbidden() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
    }
}
