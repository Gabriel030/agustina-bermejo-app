package com.red_escuela.backend.academicrecord;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CalendarFeedTestProviderConfiguration.class)
class CalendarFeedSecurityIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(81_000_000);
    private static final AtomicInteger CLIENTS = new AtomicInteger(0);
    private static final String UNKNOWN_TOKEN = "Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmE";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        CalendarFeedTestProviderConfiguration.EVENTS_BY_STUDENT.clear();
        CalendarFeedTestProviderConfiguration.LAST_RANGE_BY_STUDENT.clear();
    }

    private record Actor(Person person, Student student) {
    }

    private Actor newStudent() {
        int number = SEQUENCE.incrementAndGet();
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Nombre");
        person.setLastname("Apellido " + number);
        person.setUsername("calfeed." + number);
        person.setDni(String.valueOf(number));
        person.setEmail("calfeed." + number + "@example.com");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        Institution institution = new Institution();
        institution.setName("Instituto " + number);
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        return new Actor(person, student);
    }

    private String bearer(Actor actor) {
        return "Bearer " + jwtService.generateAccessToken(actor.person());
    }

    private static RequestPostProcessor fromClient(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String uniqueClient() {
        int value = CLIENTS.incrementAndGet();
        return "10." + (value / 65536 % 256) + "." + (value / 256 % 256) + "." + (value % 256);
    }

    private String rotateToken(Actor actor) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/me/calendar/feed/token").header("Authorization", bearer(actor)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).get("token").asString();
    }

    private ResultActions fetchFeed(String token, String client) throws Exception {
        return mockMvc.perform(get("/public/calendar/" + token + ".ics").with(fromClient(client)));
    }

    private String feedBody(String token) throws Exception {
        return fetchFeed(token, uniqueClient()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private record Observed(int status, String body, String contentType, String cacheControl) {
    }

    private Observed observe(String token) throws Exception {
        MockHttpServletResponse response = fetchFeed(token, uniqueClient()).andReturn().getResponse();
        return new Observed(response.getStatus(), response.getContentAsString(), response.getContentType(),
                response.getHeader("Cache-Control"));
    }

    @Test
    void unknownRevokedRegeneratedAndMalformedTokensReturnTheSameNotFound() throws Exception {
        Actor owner = newStudent();
        String revoked = rotateToken(owner);
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        String regenerated = rotateToken(owner);
        String current = rotateToken(owner);
        assertThat(observe(current).status()).isEqualTo(200);

        Observed unknown = observe(UNKNOWN_TOKEN);
        Observed afterRevocation = observe(revoked);
        Observed afterRegeneration = observe(regenerated);
        Observed tooShort = observe("abc");
        Observed tooLong = observe("A".repeat(200));
        Observed badCharacters = observe("!".repeat(43));

        assertThat(unknown.status()).isEqualTo(404);
        for (Observed other : List.of(afterRevocation, afterRegeneration, tooShort, tooLong, badCharacters)) {
            assertThat(other).isEqualTo(unknown);
        }
        assertThat(unknown.body()).doesNotContain(UNKNOWN_TOKEN);
    }

    @Test
    void regeneratingInvalidatesThePreviousTokenAndKeepsASingleActiveRow() throws Exception {
        Actor owner = newStudent();
        String first = rotateToken(owner);
        String second = rotateToken(owner);

        assertThat(first).isNotEqualTo(second);
        fetchFeed(first, uniqueClient()).andExpect(status().isNotFound());
        fetchFeed(second, uniqueClient()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ? and revoked_at is null",
                Integer.class, owner.student().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ?",
                Integer.class, owner.student().getId())).isEqualTo(2);
    }

    @Test
    void databaseRejectsASecondActiveTokenForTheSameStudent() {
        Actor owner = newStudent();
        jdbc.update("insert into calendar_feed_token (student_id, token_hash, created_at) values (?, ?, now())",
                owner.student().getId(), "a".repeat(64));

        assertThatThrownBy(() -> jdbc.update("insert into calendar_feed_token (student_id, token_hash, created_at) values (?, ?, now())",
                owner.student().getId(), "b".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);

        List<Map<String, Object>> rows = jdbc.queryForList("select * from calendar_feed_token where student_id = ?", owner.student().getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).values().toString()).doesNotContain(token);
        assertThat((String) rows.get(0).get("token_hash")).hasSize(64).isNotEqualTo(token);
    }

    @Test
    void feedStatusNeverExposesTheToken() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);

        String body = mockMvc.perform(get("/me/calendar/feed").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(body).doesNotContain(token);
        assertThat(json.get("active").asBoolean()).isTrue();
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("active", "createdAt", "lastUsedAt");
    }

    @Test
    void createdTokenResponseCarriesUrlTokenAndCreatedAtOnlyOnce() throws Exception {
        Actor owner = newStudent();

        String body = mockMvc.perform(post("/me/calendar/feed/token").header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("url", "token", "createdAt");
        assertThat(json.get("url").asString()).endsWith("/public/calendar/" + json.get("token").asString() + ".ics");
        assertThat(json.get("token").asString()).hasSize(43);
    }

    @Test
    void tokenNeverAppearsInApplicationLogs() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Actor owner = newStudent();
        try {
            String token = rotateToken(owner);
            fetchFeed(token, uniqueClient()).andExpect(status().isOk());
            mockMvc.perform(get("/public/calendar/" + token).with(fromClient(uniqueClient())));
            mockMvc.perform(post("/public/calendar/" + token + ".ics").with(fromClient(uniqueClient())));
            mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
            fetchFeed(token, uniqueClient()).andExpect(status().isNotFound());
            String fragment = token.substring(0, 12);

            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(fragment)
                    || String.valueOf(event.getThrowableProxy()).contains(fragment)
                    || String.valueOf(event.getArgumentArray() == null ? "" : List.of(event.getArgumentArray())).contains(fragment));
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void auditRecordsRotationAndRevocationWithoutTheToken() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select action, entity_type, entity_id, before_value, after_value, reason, actor_person_id from audit_log "
                        + "where entity_id = ? and action like 'CALENDAR_FEED_TOKEN%' order by id", owner.student().getId());

        assertThat(rows).extracting(row -> row.get("action")).containsExactly("CALENDAR_FEED_TOKEN_ROTATED", "CALENDAR_FEED_TOKEN_REVOKED");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("entity_type")).isEqualTo("STUDENT");
            assertThat(row.get("actor_person_id")).isEqualTo(owner.person().getId());
            assertThat(row.values().toString()).doesNotContain(token);
        });
    }

    @Test
    void deleteIsIdempotentAndWorksWithoutAnActiveToken() throws Exception {
        Actor owner = newStudent();

        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        rotateToken(owner);
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        mockMvc.perform(get("/me/calendar/feed").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ? and revoked_at is null",
                Integer.class, owner.student().getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and action = 'CALENDAR_FEED_TOKEN_REVOKED'",
                Integer.class, owner.student().getId())).isEqualTo(1);
    }

    @Test
    void aStudentCannotSeeOrRegenerateTheTokenOfAnotherStudent() throws Exception {
        Actor alice = newStudent();
        Actor bob = newStudent();
        String aliceToken = rotateToken(alice);

        String bobStatus = mockMvc.perform(get("/me/calendar/feed").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/me/calendar/feed/token").header("Authorization", bearer(bob))).andExpect(status().isNoContent());
        String bobToken = rotateToken(bob);

        assertThat(objectMapper.readTree(bobStatus).get("active").asBoolean()).isFalse();
        assertThat(bobToken).isNotEqualTo(aliceToken);
        fetchFeed(aliceToken, uniqueClient()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ? and revoked_at is null",
                Integer.class, alice.student().getId())).isEqualTo(1);
    }

    @Test
    void feedAndExportContainOnlyTheOwnersEvents() throws Exception {
        Actor alice = newStudent();
        Actor bob = newStudent();
        CalendarFeedTestProviderConfiguration.EVENTS_BY_STUDENT.put(alice.student().getId(),
                List.of(CalendarFeedTestProviderConfiguration.classEvent("class-a", "Clase de Alice", CalendarEventStatus.SCHEDULED)));
        CalendarFeedTestProviderConfiguration.EVENTS_BY_STUDENT.put(bob.student().getId(),
                List.of(CalendarFeedTestProviderConfiguration.classEvent("class-b", "Clase de Bob", CalendarEventStatus.CANCELED)));
        String aliceToken = rotateToken(alice);

        String feed = feedBody(aliceToken);
        String export = mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String bobExport = mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(feed).contains("Clase de Alice").doesNotContain("Clase de Bob");
        assertThat(export).contains("Clase de Alice").doesNotContain("Clase de Bob");
        assertThat(bobExport).contains("Clase de Bob").contains("STATUS:CANCELLED").doesNotContain("Clase de Alice");
    }

    @Test
    void publicFeedUsesTheRollingWindowAndSecurityHeaders() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);

        MockHttpServletResponse response = fetchFeed(token, uniqueClient()).andExpect(status().isOk()).andReturn().getResponse();

        LocalDate today = LocalDate.now(MutableClock.INSTANCE);
        LocalDate[] range = CalendarFeedTestProviderConfiguration.LAST_RANGE_BY_STUDENT.get(owner.student().getId());
        assertThat(range[0]).isEqualTo(today.minusDays(14));
        assertThat(range[1]).isEqualTo(today.plusDays(120));
        assertThat(response.getContentType()).isEqualTo("text/calendar;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).contains("private").contains("max-age=900").doesNotContain("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void repeatedMissesFromOneClientAreRateLimitedWithRetryAfter() throws Exception {
        String attacker = uniqueClient();
        Integer limitedStatus = null;
        MockHttpServletResponse limited = null;

        for (int attempt = 0; attempt < 40 && limitedStatus == null; attempt++) {
            MockHttpServletResponse response = fetchFeed(UNKNOWN_TOKEN, attacker).andReturn().getResponse();
            if (response.getStatus() != 404) {
                limitedStatus = response.getStatus();
                limited = response;
            }
        }

        assertThat(limitedStatus).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        fetchFeed(UNKNOWN_TOKEN, uniqueClient()).andExpect(status().isNotFound());
    }

    @Test
    void ipBlockedByMissesStillServesValidTokensButKeepsRejectingMisses() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);
        String attacker = uniqueClient();
        for (int attempt = 0; attempt < 40; attempt++) {
            fetchFeed(UNKNOWN_TOKEN, attacker);
        }
        fetchFeed(UNKNOWN_TOKEN, attacker).andExpect(status().isTooManyRequests());

        fetchFeed(token, attacker).andExpect(status().isOk());
        fetchFeed(UNKNOWN_TOKEN, attacker).andExpect(status().isTooManyRequests());
    }

    @Test
    void validTokenFetchesDoNotConsumeTheClientMissBudget() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);
        String sharedGoogleLikeClient = uniqueClient();

        for (int attempt = 0; attempt < 30; attempt++) {
            fetchFeed(token, sharedGoogleLikeClient).andExpect(status().isOk());
        }

        fetchFeed(UNKNOWN_TOKEN, sharedGoogleLikeClient).andExpect(status().isNotFound());
    }

    @Test
    void aSingleTokenIsRateLimitedPerHourEvenAcrossClients() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);
        int served = 0;
        MockHttpServletResponse limited = null;

        for (int attempt = 0; attempt < 130 && limited == null; attempt++) {
            MockHttpServletResponse response = fetchFeed(token, uniqueClient()).andReturn().getResponse();
            if (response.getStatus() == 200) {
                served++;
            } else {
                limited = response;
            }
        }

        assertThat(served).isEqualTo(120);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
    }

    @Test
    void lastUsedAtIsUpdatedAtMostEveryTenMinutes() throws Exception {
        Actor owner = newStudent();
        String token = rotateToken(owner);
        assertThat(lastUsedAt(owner)).isNull();

        fetchFeed(token, uniqueClient()).andExpect(status().isOk());
        Object firstUse = lastUsedAt(owner);
        assertThat(firstUse).isNotNull();

        MutableClock.INSTANCE.advance(Duration.ofMinutes(9));
        fetchFeed(token, uniqueClient()).andExpect(status().isOk());
        assertThat(lastUsedAt(owner)).isEqualTo(firstUse);

        MutableClock.INSTANCE.advance(Duration.ofMinutes(2));
        fetchFeed(token, uniqueClient()).andExpect(status().isOk());
        assertThat(lastUsedAt(owner)).isNotEqualTo(firstUse);
    }

    private Object lastUsedAt(Actor owner) {
        return jdbc.queryForObject("select last_used_at from calendar_feed_token where student_id = ? and revoked_at is null",
                Object.class, owner.student().getId());
    }

    @Test
    void exportValidatesTheRangeAndDefaultsToSixtyTwoDays() throws Exception {
        Actor owner = newStudent();
        LocalDate today = LocalDate.now(MutableClock.INSTANCE);

        mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(owner))).andExpect(status().isOk());
        LocalDate[] range = CalendarFeedTestProviderConfiguration.LAST_RANGE_BY_STUDENT.get(owner.student().getId());
        assertThat(range[0]).isEqualTo(today);
        assertThat(range[1]).isEqualTo(today.plusDays(61));
        mockMvc.perform(get("/me/calendar/export.ics").param("from", "2026-01-01").param("to", "2026-03-03")
                .header("Authorization", bearer(owner))).andExpect(status().isOk());
        mockMvc.perform(get("/me/calendar/export.ics").param("from", "2026-01-01").param("to", "2026-03-04")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/me/calendar/export.ics").param("from", "2026-02-02").param("to", "2026-02-01")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/me/calendar/export.ics").param("from", "2026-02-02")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
    }

    @Test
    void exportIsADownloadAndRequiresAuthentication() throws Exception {
        Actor owner = newStudent();

        MockHttpServletResponse response = mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getContentType()).isEqualTo("text/calendar;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains("calendario.ics");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        mockMvc.perform(get("/me/calendar/export.ics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/calendar/feed")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/me/calendar/feed/token")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/me/calendar/feed/token")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicMatcherOnlyOpensTheGetFeed() throws Exception {
        mockMvc.perform(post("/public/calendar/" + UNKNOWN_TOKEN + ".ics").with(fromClient(uniqueClient())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/public/calendar/" + UNKNOWN_TOKEN + ".ics").with(fromClient(uniqueClient())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/public/calendar/nested/" + UNKNOWN_TOKEN + ".ics").with(fromClient(uniqueClient())))
                .andExpect(status().isUnauthorized());
    }
}
