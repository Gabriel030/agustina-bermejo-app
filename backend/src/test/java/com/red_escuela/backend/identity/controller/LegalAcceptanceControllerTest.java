package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.red_escuela.backend.identity.events.GuardianConfirmationRequestedEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class LegalAcceptanceControllerTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private JwtService jwtService;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private RegisterRequest registration(String suffix, Long institutionId, Boolean terms, Boolean privacy) {
        return new RegisterRequest("Sofía", "Ramírez", "legal." + suffix + "@example.com", "legal." + suffix,
                "Test1234", institutionId, terms, privacy, LocalDate.of(2000, 1, 15), null, null, null, null);
    }

    private RegisterRequest minorRegistration(String suffix, Long institutionId, String guardianName, String guardianEmail) {
        return new RegisterRequest("Lucía", "Gómez", "legal." + suffix + "@example.com", "legal." + suffix,
                "Test1234", institutionId, true, true, LocalDate.of(2012, 5, 14), guardianName, guardianEmail, "Madre", null);
    }

    private String guardianToken(ApplicationEvents events) {
        List<GuardianConfirmationRequestedEvent> sent = events.stream(GuardianConfirmationRequestedEvent.class).toList();
        String link = sent.get(sent.size() - 1).confirmationLink();
        return link.substring(link.indexOf("token=") + 6);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/auth/legal/guardian-confirmation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private String registerAndGetToken(RegisterRequest request) throws Exception {
        String body = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit-Agent/1.0")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString("");
    }

    private Person personWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private int acceptanceCount() {
        return jdbcTemplate.queryForObject("select count(*) from legal_acceptance", Integer.class);
    }

    @Test
    void currentDocumentsArePublicAndListBothVersions() throws Exception {
        mockMvc.perform(get("/legal/documents/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].type").value("TERMS"))
                .andExpect(jsonPath("$.documents[0].version").value("2026-11-01"))
                .andExpect(jsonPath("$.documents[1].type").value("PRIVACY"));
    }

    @Test
    void registeringWithoutAcceptanceFlagsIsRejectedAndCreatesNothing() throws Exception {
        Long institutionId = persistInstitution();
        int before = acceptanceCount();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration("missing", institutionId, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));

        assertThat(personRepository.findByUsername("legal.missing")).isEmpty();
        assertThat(acceptanceCount()).isEqualTo(before);
    }

    @Test
    void registeringWithOnlyOneAcceptedDocumentIsRejected() throws Exception {
        Long institutionId = persistInstitution();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration("terms-only", institutionId, true, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration("privacy-only", institutionId, false, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));

        assertThat(personRepository.findByUsername("legal.terms-only")).isEmpty();
        assertThat(personRepository.findByUsername("legal.privacy-only")).isEmpty();
    }

    @Test
    void registeringAcceptsBothCurrentDocumentsWithClockTimeAndEvidence() throws Exception {
        Long institutionId = persistInstitution();
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 10, 30);
        MutableClock.INSTANCE.setDateTime(now);

        String token = registerAndGetToken(registration("ok", institutionId, true, true));

        Long personId = personRepository.findByUsername("legal.ok").orElseThrow().getId();
        assertThat(jdbcTemplate.queryForList(
                "select document_type from legal_acceptance where person_id = ? order by document_type", String.class, personId))
                .containsExactly("PRIVACY", "TERMS");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from legal_acceptance where person_id = ? and accepted_at = ? and version = '2026-11-01'"
                        + " and ip_hash is not null and user_agent_summary = 'JUnit-Agent/1.0'",
                Integer.class, personId, java.sql.Timestamp.valueOf(now))).isEqualTo(2);
        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isEmpty());
    }

    @Test
    void personWithoutAcceptanceSeesBothPendingAndAcceptingIsIdempotent() throws Exception {
        Person person = personWithRole("legal-pending", "ALUMNO");
        String token = jwtService.generateAccessToken(person);

        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(2));

        mockMvc.perform(post("/me/legal-acceptances").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isEmpty());
        mockMvc.perform(post("/me/legal-acceptances").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isEmpty());

        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?",
                Integer.class, person.getId())).isEqualTo(2);
    }

    @Test
    void anOutdatedVersionStaysPendingUntilTheCurrentOneIsAccepted() throws Exception {
        Person person = personWithRole("legal-outdated", "ALUMNO");
        String token = jwtService.generateAccessToken(person);
        jdbcTemplate.update("insert into legal_acceptance (person_id, document_type, version, accepted_at)"
                + " values (?, 'TERMS', '2025-01-01', now()), (?, 'PRIVACY', '2026-11-01', now())",
                person.getId(), person.getId());

        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].type").value("TERMS"))
                .andExpect(jsonPath("$.documents[0].version").value("2026-11-01"));
    }

    @Test
    void staffListsWhoAcceptedWhatAndOtherRolesCannot() throws Exception {
        Person student = personWithRole("legal-listed", "ALUMNO");
        mockMvc.perform(post("/me/legal-acceptances")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(student)))
                .andExpect(status().isOk());
        String director = jwtService.generateAccessToken(personWithRole("legal-director", "DIRECTOR"));
        String administrative = jwtService.generateAccessToken(personWithRole("legal-admin", "ADMINISTRATIVO"));
        String teacher = jwtService.generateAccessToken(personWithRole("legal-teacher", "DOCENTE"));

        for (String token : new String[] {director, administrative}) {
            mockMvc.perform(get("/legal-acceptances").param("personId", student.getId().toString())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.items[0].personId").value(student.getId()))
                    .andExpect(jsonPath("$.items[0].version").value("2026-11-01"));
        }
        mockMvc.perform(get("/legal-acceptances").param("personId", student.getId().toString())
                        .param("documentType", "PRIVACY").header("Authorization", "Bearer " + director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].documentType").value("PRIVACY"));
        mockMvc.perform(get("/legal-acceptances").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/legal-acceptances")).andExpect(status().isUnauthorized());
    }

    @Test
    void minorWithoutGuardianDataIsRejectedAndAdultWithoutBirthDateToo() throws Exception {
        Long institutionId = persistInstitution();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minorRegistration("minor-none", institutionId, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MINOR_GUARDIAN_REQUIRED"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minorRegistration("minor-blank", institutionId, "  ", "madre@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MINOR_GUARDIAN_REQUIRED"));
        RegisterRequest noBirthDate = new RegisterRequest("Ana", "Perez", "legal.nobd@example.com", "legal.nobd", "Test1234",
                institutionId, true, true, null, null, null, null, null);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noBirthDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIRTH_DATE_REQUIRED"));

        assertThat(personRepository.findByUsername("legal.minor-none")).isEmpty();
        assertThat(personRepository.findByUsername("legal.nobd")).isEmpty();
    }

    @Test
    void minorStaysPendingUntilTheGuardianConfirmsByMailLinkAndCannotAcceptAlone() throws Exception {
        Long institutionId = persistInstitution();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));

        String minorToken = registerAndGetToken(minorRegistration("minor-ok", institutionId, "Marcela Gómez", "marcela@example.com"));

        Long personId = personRepository.findByUsername("legal.minor-ok").orElseThrow().getId();
        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?", Integer.class, personId)).isZero();
        GuardianConfirmationRequestedEvent sent = applicationEvents.stream(GuardianConfirmationRequestedEvent.class).findFirst().orElseThrow();
        assertThat(sent.guardianEmail()).isEqualTo("marcela@example.com");
        assertThat(sent.minorFirstName()).isEqualTo("Lucía");
        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.guardianConfirmationRequired").value(true));
        mockMvc.perform(post("/me/legal-acceptances").header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUARDIAN_ACCEPTANCE_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?", Integer.class, personId)).isZero();

        confirm(guardianToken(applicationEvents)).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?", Integer.class, personId)).isEqualTo(2);
        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + minorToken))
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.guardianConfirmationRequired").value(false));
    }

    @Test
    void guardianTokenCannotBeReusedNorUsedAfterItExpiresNorGuessed() throws Exception {
        Long institutionId = persistInstitution();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));
        registerAndGetToken(minorRegistration("minor-reuse", institutionId, "Marcela Gómez", "marcela@example.com"));
        String firstToken = guardianToken(applicationEvents);
        confirm(firstToken).andExpect(status().isNoContent());
        confirm(firstToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GUARDIAN_CONFIRMATION_TOKEN"));
        confirm("not-a-real-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GUARDIAN_CONFIRMATION_TOKEN"));

        registerAndGetToken(minorRegistration("minor-expired", institutionId, "Marcela Gómez", "marcela2@example.com"));
        String expiringToken = guardianToken(applicationEvents);
        MutableClock.INSTANCE.advance(Duration.ofHours(73));
        confirm(expiringToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GUARDIAN_CONFIRMATION_TOKEN"));
        Long expiredPersonId = personRepository.findByUsername("legal.minor-expired").orElseThrow().getId();
        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?", Integer.class, expiredPersonId)).isZero();
    }

    @Test
    void resendIsLimitedByCooldownAndInvalidatesThePreviousLink() throws Exception {
        Long institutionId = persistInstitution();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));
        String minorToken = registerAndGetToken(minorRegistration("minor-resend", institutionId, "Marcela Gómez", "marcela@example.com"));
        String firstLink = guardianToken(applicationEvents);

        mockMvc.perform(post("/me/legal-acceptances/guardian-confirmation/resend").header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONFIRMATION_RESEND_LIMIT"));

        MutableClock.INSTANCE.advance(Duration.ofMinutes(11));
        mockMvc.perform(post("/me/legal-acceptances/guardian-confirmation/resend").header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isNoContent());

        assertThat(applicationEvents.stream(GuardianConfirmationRequestedEvent.class).count()).isEqualTo(2);
        confirm(firstLink).andExpect(status().isBadRequest());
        confirm(guardianToken(applicationEvents)).andExpect(status().isNoContent());
    }

    @Test
    void resendDoesNotApplyToAdults() throws Exception {
        Person adult = personWithRole("legal-adult-resend", "ALUMNO");
        mockMvc.perform(post("/me/legal-acceptances/guardian-confirmation/resend")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(adult)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONFIRMATION_NOT_APPLICABLE"));
    }

    @Test
    void newsletterPreferenceIsSeparateFromAcceptanceAndChangeable() throws Exception {
        Long institutionId = persistInstitution();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));
        RegisterRequest request = new RegisterRequest("Sofía", "Ramírez", "legal.news@example.com", "legal.news", "Test1234",
                institutionId, true, true, LocalDate.of(2000, 1, 15), null, null, null, true);
        String token = registerAndGetToken(request);

        mockMvc.perform(get("/me/newsletter-preference").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.optedIn").value(true));
        MutableClock.INSTANCE.advance(Duration.ofDays(1));
        token = jwtService.generateAccessToken(personRepository.findByUsername("legal.news").orElseThrow());
        mockMvc.perform(put("/me/newsletter-preference").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"optedIn\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(false))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-14T10:00:00"));
        entityManager.clear();
        mockMvc.perform(get("/me/newsletter-preference").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.optedIn").value(false));

        String withoutRow = jwtService.generateAccessToken(personWithRole("legal-news-none", "ALUMNO"));
        mockMvc.perform(get("/me/newsletter-preference").header("Authorization", "Bearer " + withoutRow))
                .andExpect(jsonPath("$.optedIn").value(false))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void pendingReturnsTheSuggestedDeadlineAndNeverBlocksAfterTheGracePeriod() throws Exception {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 13, 10, 0));
        String token = jwtService.generateAccessToken(personWithRole("legal-grace", "ALUMNO"));

        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.suggestedDeadline").value("2026-12-13"))
                .andExpect(jsonPath("$.gracePeriodExpired").value(false));

        MutableClock.INSTANCE.advance(Duration.ofDays(30));
        token = jwtService.generateAccessToken(personRepository.findByUsername("legal-grace").orElseThrow());
        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.gracePeriodExpired").value(false));

        MutableClock.INSTANCE.advance(Duration.ofDays(2));
        token = jwtService.generateAccessToken(personRepository.findByUsername("legal-grace").orElseThrow());
        mockMvc.perform(get("/me/legal-acceptances/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedDeadline").value("2026-12-13"))
                .andExpect(jsonPath("$.gracePeriodExpired").value(true))
                .andExpect(jsonPath("$.documents.length()").value(2));
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }
}
