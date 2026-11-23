package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InvitationWaveControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PermissionSnapshotCache permissionCache;
    @Value("${app.broadcast.providers.brevo.daily-limit:300}")
    private int globalDailyLimit;
    @Value("${app.broadcast.dispatch.max-per-run:100}")
    private int maxPerRun;
    @Value("${app.invitation-wave.max-recipients:5000}")
    private int maxRecipients;
    @Value("${app.timezone}")
    private String zone;

    private Long directorId;
    private long batchId;
    private String suffix;

    @BeforeEach
    void prepare() {
        cleanTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        directorId = transactions.execute(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            return AudienceFreezeFixtures.person(entityManager, roleRepository, "wave-http-director-" + suffix, true, "DIRECTOR").getId();
        });
        batchId = createImportBatch("COMMITTED");
    }

    @AfterEach
    void restore() {
        jdbc.update("delete from permission_module_mode where module in ('notification', 'identity') and role = 'ADMINISTRATIVO'");
        jdbc.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code in ('notification.send_invitation_wave', 'notification.manage_invitation_waves', 'notification.read_invitation_waves', 'identity.read_students') on conflict do nothing");
        permissionCache.invalidate();
        cleanTables();
    }

    private void cleanTables() {
        jdbc.update("delete from invitation_wave_recipient");
        jdbc.update("delete from invitation_wave");
        jdbc.update("delete from import_created_entity where batch_id in (select id from import_batch where name like 'Lote http %')");
        jdbc.update("delete from import_batch where name like 'Lote http %'");
    }

    private long createImportBatch(String batchStatus) {
        return jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at, committed_at, version)
                values ('Lote http ' || ? || ' ' || ?, ?, (select id from institution limit 1), ?, now(), now(), 0) returning id
                """, Long.class, suffix, batchStatus, batchStatus, directorId);
    }

    private long importedStudent(long inBatchId, boolean withEmail, String password) {
        long personId = transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            String unique = UUID.randomUUID().toString();
            Person person = new Person();
            person.setName("Importada");
            person.setLastname("Apellido" + suffix);
            person.setDni(unique.substring(0, 12));
            person.setUsername("imported." + unique);
            person.setEmail(withEmail ? "imported." + unique + "@example.com" : null);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.setPassword(password);
            person.setGrantSystemAccess(false);
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return person.getId();
        });
        jdbc.update("""
                insert into import_created_entity (batch_id, entity_type, entity_id, natural_key, created_at)
                values (?, 'PERSONS', ?, ?, now())
                """, inBatchId, personId, "dni-" + inBatchId + "-" + personId);
        return personId;
    }

    private String waveBody() {
        return waveBody(batchId);
    }

    private String waveBody(long inBatchId) {
        return "{\"name\":\"Oleada " + suffix + "\",\"importBatchId\":" + inBatchId
                + ",\"batchSize\":10,\"intervalMinutes\":15,\"dailyCap\":100}";
    }

    private MockHttpServletRequestBuilder asRole(MockHttpServletRequestBuilder request, String role) {
        return request.with(user(String.valueOf(directorId)).roles(role));
    }

    private ResultActions createWave(String role) throws Exception {
        return mockMvc.perform(asRole(post("/invitation-waves"), role).contentType(MediaType.APPLICATION_JSON).content(waveBody()));
    }

    private long createWaveId() throws Exception {
        String body = createWave("DIRECTOR").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    private void removePermissionFromAdministrativo(String module, String code) {
        jdbc.update("insert into permission_module_mode (role, module, mode) values ('ADMINISTRATIVO', ?, 'ENFORCE') on conflict (role, module) do update set mode = 'ENFORCE'", module);
        jdbc.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id = (select id from permission where code = ?)", code);
        permissionCache.invalidate();
    }

    @Test
    void creatingAWaveAnswers201WithThePreviewForAdministrativoAndDirector() throws Exception {
        importedStudent(batchId, true, null);
        importedStudent(batchId, true, null);
        importedStudent(batchId, false, null);
        importedStudent(batchId, true, "already-active-hash");

        createWave("ADMINISTRATIVO").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.importBatchId").value(batchId))
                .andExpect(jsonPath("$.importBatchName").value("Lote http " + suffix + " COMMITTED"))
                .andExpect(jsonPath("$.eligibleCount").value(2))
                .andExpect(jsonPath("$.excludedTotal").value(1))
                .andExpect(jsonPath("$.invitableCount").value(2))
                .andExpect(jsonPath("$.excludedByReason.NO_EMAIL").value(1))
                .andExpect(jsonPath("$.excludedByReason.ALREADY_ACTIVE").value(0))
                .andExpect(jsonPath("$.excludedByReason.NOT_ELIGIBLE").value(0))
                .andExpect(jsonPath("$.excludedByReason.IN_OTHER_WAVE").value(0))
                .andExpect(jsonPath("$.excludedByReason.WAVE_CANCELLED").value(0))
                .andExpect(jsonPath("$.nextRunAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void creatingAWaveWithoutImportBatchIsRejectedAs400() throws Exception {
        importedStudent(batchId, true, null);

        mockMvc.perform(asRole(post("/invitation-waves"), "DIRECTOR").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"batchSize\":10,\"intervalMinutes\":15,\"dailyCap\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownImportBatchAnswers404() throws Exception {
        mockMvc.perform(asRole(post("/invitation-waves"), "DIRECTOR").contentType(MediaType.APPLICATION_JSON)
                        .content(waveBody(-5L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_WAVE_IMPORT_BATCH_NOT_FOUND"));
    }

    @Test
    void rolesOtherThanAdministrativoAndDirectorAreForbiddenOnEveryEndpoint() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/invitation-waves"), get("/invitation-waves/" + waveId), get("/invitation-waves/settings"),
                get("/invitation-waves/import-batches"), get("/invitation-waves/" + waveId + "/recipients"),
                get("/invitation-waves/" + waveId + "/report.csv"), post("/invitation-waves/" + waveId + "/start"),
                post("/invitation-waves/" + waveId + "/pause"), post("/invitation-waves/" + waveId + "/resume"),
                post("/invitation-waves/" + waveId + "/cancel"));
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR")) {
            for (MockHttpServletRequestBuilder request : requests) {
                mockMvc.perform(asRole(request, role)).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(post("/invitation-waves/" + waveId + "/start")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/invitation-waves").contentType(MediaType.APPLICATION_JSON).content(waveBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void waveLifecycleRunsOverHttpWithTheAuthenticatedActor() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();

        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/start"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.startedByPersonId").value(directorId));
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/pause"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.pausedByPersonId").value(directorId));
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/resume"), "DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.resumedByPersonId").value(directorId));
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/cancel"), "DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByPersonId").value(directorId))
                .andExpect(jsonPath("$.excludedByReason.WAVE_CANCELLED").value(1))
                .andExpect(jsonPath("$.invitableCount").value(0));
    }

    @Test
    void invalidTransitionAnswers409AndUnknownWaveAnswers404() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();

        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/pause"), "DIRECTOR"))
                .andExpect(status().isConflict());
        mockMvc.perform(asRole(get("/invitation-waves/-1"), "DIRECTOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_WAVE_NOT_FOUND"));
        mockMvc.perform(asRole(get("/invitation-waves/-1/recipients"), "DIRECTOR")).andExpect(status().isNotFound());
        mockMvc.perform(asRole(get("/invitation-waves/-1/report.csv"), "DIRECTOR")).andExpect(status().isNotFound());
    }

    @Test
    void sendPermissionIsIndependentFromManagePermission() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();
        removePermissionFromAdministrativo("notification", "notification.send_invitation_wave");

        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/start"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/cancel"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/resume"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/" + waveId), "ADMINISTRATIVO")).andExpect(status().isOk());
        createWave("ADMINISTRATIVO").andExpect(status().isCreated());
    }

    @Test
    void managePermissionGuardsCreateAndPauseButNotStart() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();
        removePermissionFromAdministrativo("notification", "notification.manage_invitation_waves");

        createWave("ADMINISTRATIVO").andExpect(status().isForbidden());
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/pause"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/start"), "ADMINISTRATIVO")).andExpect(status().isOk());
    }

    @Test
    void readPermissionGuardsEveryGetEndpoint() throws Exception {
        importedStudent(batchId, true, null);
        long waveId = createWaveId();
        removePermissionFromAdministrativo("notification", "notification.read_invitation_waves");

        mockMvc.perform(asRole(get("/invitation-waves"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/settings"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/import-batches"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/" + waveId), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/recipients"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/report.csv"), "ADMINISTRATIVO")).andExpect(status().isForbidden());
        mockMvc.perform(asRole(post("/invitation-waves/" + waveId + "/start"), "ADMINISTRATIVO")).andExpect(status().isOk());
    }

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        importedStudent(batchId, true, null);
        long first = createWaveId();
        jdbc.update("update invitation_wave set created_at = created_at - interval '1 hour' where id = ?", first);
        jdbc.update("delete from invitation_wave_recipient where wave_id = ?", first);
        jdbc.update("update invitation_wave set status = 'CANCELLED', cancelled_at = now() where id = ?", first);
        long second = createWaveId();

        mockMvc.perform(asRole(get("/invitation-waves?page=0&size=1"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(asRole(get("/invitation-waves?page=1&size=1"), "ADMINISTRATIVO"))
                .andExpect(jsonPath("$.items[0].id").value(first));
    }

    @Test
    void recipientsExposeNameAndEmailOnlyToCallersWithReadStudentsPermission() throws Exception {
        long eligible = importedStudent(batchId, true, null);
        importedStudent(batchId, false, null);
        long waveId = createWaveId();

        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/recipients?status=PENDING"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].personId").value(eligible))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].attempts").value(0))
                .andExpect(jsonPath("$.items[0].displayName").value("Importada Apellido" + suffix))
                .andExpect(jsonPath("$.items[0].email").value(org.hamcrest.Matchers.endsWith("@example.com")));
        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/recipients?status=EXCLUDED"), "DIRECTOR"))
                .andExpect(jsonPath("$.items[0].exclusionReason").value("NO_EMAIL"))
                .andExpect(jsonPath("$.items[0].email").value(org.hamcrest.Matchers.nullValue()));

        removePermissionFromAdministrativo("identity", "identity.read_students");

        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/recipients"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].displayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].email").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].displayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].email").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void recipientLastAttemptIsTheLatestOfSendAndDeliveryCreation() throws Exception {
        long person = importedStudent(batchId, true, null);
        long waveId = createWaveId();
        Long deliveryId = jdbc.queryForObject("""
                insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, scheduled_for, body_text, subject, created_at)
                values (?, 'EMAIL', 'BROADCAST', 'K', 1, ?, 'QUEUED', current_date, 'texto', 'asunto', now() - interval '2 hours') returning id
                """, Long.class, "http-it-" + suffix, person);
        jdbc.update("update invitation_wave_recipient set status = 'SENT', delivery_id = ?, attempts = 1, sent_at = now() - interval '1 hour' where wave_id = ? and person_id = ?",
                deliveryId, waveId, person);

        mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/recipients"), "DIRECTOR"))
                .andExpect(jsonPath("$.items[0].sentAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].lastAttemptAt").isNotEmpty());
        jdbc.update("delete from invitation_wave_recipient where wave_id = ?", waveId);
        jdbc.update("delete from notification_delivery where id = ?", deliveryId);
    }

    @Test
    void reportCsvHasHeaderBomFilenameAndNoPersonalData() throws Exception {
        long withEmail = importedStudent(batchId, true, null);
        long withoutEmail = importedStudent(batchId, false, null);
        long waveId = createWaveId();
        String expectedName = "invitaciones-oleada-" + waveId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        byte[] body = mockMvc.perform(asRole(get("/invitation-waves/" + waveId + "/report.csv"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("filename=\"" + expectedName + "\"")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
        String csv = new String(body, StandardCharsets.UTF_8).substring(1);
        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).isEqualTo("personId,status,exclusionReason,lastErrorCode,attempts,sentAt");
        assertThat(lines).hasSize(3);
        assertThat(lines).contains(withEmail + ",PENDING,,,0,", withoutEmail + ",EXCLUDED,NO_EMAIL,,0,");
        assertThat(csv).doesNotContain("@").doesNotContain("Importada").doesNotContain("Apellido");
    }

    @Test
    void settingsComeFromTheRealConfiguration() throws Exception {
        mockMvc.perform(asRole(get("/invitation-waves/settings"), "DIRECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalDailyCap").value(globalDailyLimit))
                .andExpect(jsonPath("$.maxBatchSize").value(maxPerRun))
                .andExpect(jsonPath("$.maxRecipients").value(maxRecipients))
                .andExpect(jsonPath("$.sendWindow.start").isNotEmpty())
                .andExpect(jsonPath("$.sendWindow.end").isNotEmpty())
                .andExpect(jsonPath("$.sendWindow.zone").value(zone));
    }

    @Test
    void importBatchesListOnlyImportedBatchesWithPendingStudents() throws Exception {
        importedStudent(batchId, true, null);
        importedStudent(batchId, true, null);
        importedStudent(batchId, true, "already-active-hash");
        long activatedOnly = createImportBatch("SEALED");
        importedStudent(activatedOnly, true, "already-active-hash");
        long rolledBack = createImportBatch("ROLLED_BACK");
        importedStudent(rolledBack, true, null);
        long rollingBack = createImportBatch("ROLLING_BACK");
        importedStudent(rollingBack, true, null);
        createImportBatch("VALIDATED");

        String body = mockMvc.perform(asRole(get("/invitation-waves/import-batches"), "ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.name =~ /Lote http " + suffix + ".*/)].id");
        assertThat(ids).containsExactly((int) batchId);
        List<Integer> pending = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.id == " + batchId + ")].studentsPendingActivation");
        assertThat(pending).containsExactly(2);
        List<String> committedAt = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.id == " + batchId + ")].committedAt");
        assertThat(committedAt).hasSize(1);
    }
}
