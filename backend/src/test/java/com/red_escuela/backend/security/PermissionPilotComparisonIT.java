package com.red_escuela.backend.security;

import com.red_escuela.backend.identity.specialist.PermissionDiscrepancyAccumulator;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SlowTest(reason = "recorre los endpoints piloto de announcement y audit-log por rol y por modo contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PermissionPilotComparisonIT extends AuthorizationMatrixSupport {

    private static final List<String> PILOT_MODULES = List.of("announcement", "audit");

    private static final String AUDIT_LOG_KEY = "GET /audit-log";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;
    @Autowired
    private EntityManager announcementEntityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PermissionDiscrepancyAccumulator discrepancyAccumulator;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    @AfterEach
    void restoreDefaultGrid() {
        jdbcTemplate.update("delete from permission_discrepancy_counter");
        jdbcTemplate.update("delete from permission_module_mode");
        jdbcTemplate.update("delete from role_permission where role = 'DOCENTE' and permission_id = (select id from permission where code = 'announcement.update')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.update'");
        jdbcTemplate.update("delete from role_permission where role in ('PRECEPTOR', 'ADMINISTRATIVO') and permission_id in (select id from permission where code in ('announcement.update', 'audit.read'))");
        jdbcTemplate.update("delete from role_permission where role in ('DOCENTE', 'PRECEPTOR') and permission_id = (select id from permission where code = 'announcement.view_read_receipts')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'announcement.view_read_receipts'");
        discrepancyAccumulator.flush();
        jdbcTemplate.update("delete from permission_discrepancy_counter");
        jdbcTemplate.update("delete from audit_log where action = 'PERMISSION_GRID_CHANGED'");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> pilotRows() {
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> row.key().equals(AUDIT_LOG_KEY) || isAnnouncementPilotPath(row.path()))
                .toList();
    }

    private static boolean isAnnouncementPilotPath(String path) {
        return path.matches("^/(subject|course)-sections/\\{sectionId}/(announcements.*|audience-segments|audience-preview)$")
                || path.matches("^/announcements/\\{id}(/recipients)?$");
    }

    private Map<String, MappedEndpoint> endpointsByKey() {
        return mappedEndpoints().stream().collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
    }

    private void setPilotMode(String mode) {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
            for (String module : PILOT_MODULES) {
                jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, module, mode);
            }
        }
        cache.invalidate();
    }

    private void setGrant(String role, String permissionCode, boolean granted) throws Exception {
        String body = "{\"reason\":\"Prueba de comparacion del piloto\",\"changes\":[{\"role\":\"" + role
                + "\",\"permissionCode\":\"" + permissionCode + "\",\"granted\":" + granted + "}]}";
        int status = mockMvc.perform(put("/permissions/grid").header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.DIRECTOR))
                .contentType(APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    private boolean isForbiddenByTheGate(String key, String body, MatrixRole role) throws Exception {
        int status = statusOf(endpointsByKey().get(key), body == null ? "" : body, role);
        return status == 403 || (role == null && status == 401);
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = pilotRows();
        assertThat(rows).hasSize(14);
        List<String> mismatches = new ArrayList<>();
        for (AuthorizationMatrixRow row : rows) {
            assertThat(endpointsByKey().get(row.key())).as(row.key()).isNotNull();
            for (MatrixRole role : MatrixRole.values()) {
                boolean legacyDenied = row.byRole().get(role) == AccessExpectation.DENY;
                boolean expectedDenied = legacyDenied;
                boolean denied = isForbiddenByTheGate(row.key(), row.body(), role);
                if (denied != expectedDenied) {
                    mismatches.add(mode + " " + role + " " + row.key() + " esperado denegado=" + expectedDenied + " obtenido denegado=" + denied);
                }
            }
            if (!isForbiddenByTheGate(row.key(), row.body(), null)) {
                mismatches.add(mode + " ANONYMOUS " + row.key() + " debia denegarse");
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void legacyModeMatchesTheOldRulesForEveryPilotEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryPilotEndpointAndRole() throws Exception {
        setPilotMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeMatchesTheOldRulesForEveryPilotEndpointAndRole() throws Exception {
        setPilotMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void shadowModeWithTheDefaultGridCountsNoDivergence() throws Exception {
        setPilotMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
        discrepancyAccumulator.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from permission_discrepancy_counter", Integer.class)).isZero();
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsRevokedOrGranted() throws Exception {
        setPilotMode("ENFORCE");
        String updateKey = "PUT /announcements/{id}";
        String updateBody = rowOf(updateKey).body();
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isForbiddenByTheGate(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isTrue();

        setGrant("DOCENTE", "announcement.update", false);
        setGrant("PRECEPTOR", "announcement.update", true);
        setGrant("ADMINISTRATIVO", "audit.read", true);

        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.DOCENTE)).isTrue();
        assertThat(isForbiddenByTheGate("DELETE /announcements/{id}", "", MatrixRole.DOCENTE)).isFalse();
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isForbiddenByTheGate(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isFalse();

        setGrant("DOCENTE", "announcement.update", true);
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
    }

    @Test
    void shadowModeKeepsTheOldDecisionAndCountsTheGridDivergence() throws Exception {
        setPilotMode("SHADOW");
        String updateKey = "PUT /announcements/{id}";
        String updateBody = rowOf(updateKey).body();

        setGrant("DOCENTE", "announcement.update", false);
        setGrant("PRECEPTOR", "announcement.update", true);

        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
        discrepancyAccumulator.flush();

        Map<String, Long> counts = jdbcTemplate.queryForList(
                        "select c.role, c.occurrences from permission_discrepancy_counter c join permission p on p.id = c.permission_id where p.code = 'announcement.update'")
                .stream().collect(Collectors.toMap(row -> (String) row.get("role"), row -> ((Number) row.get("occurrences")).longValue()));
        assertThat(counts).containsEntry("DOCENTE", 1L).containsEntry("PRECEPTOR", 1L);
    }

    @Test
    void legacyModeIgnoresTheGridAndCountsNothing() throws Exception {
        String updateKey = "PUT /announcements/{id}";
        String updateBody = rowOf(updateKey).body();

        setGrant("DOCENTE", "announcement.update", false);
        setGrant("PRECEPTOR", "announcement.update", true);
        setGrant("ADMINISTRATIVO", "audit.read", true);

        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isForbiddenByTheGate(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isForbiddenByTheGate(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isTrue();
        discrepancyAccumulator.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from permission_discrepancy_counter", Integer.class)).isZero();
    }

    private record Teacher(Long personId, String token) {
    }

    private Teacher teacherWithRoles(String... roleNames) {
        return transactionTemplate.execute(status -> {
            CycleTestFixtures cycleFixtures = new CycleTestFixtures(announcementEntityManager);
            Person person = cycleFixtures.person();
            for (String roleName : roleNames) {
                person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            }
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            announcementEntityManager.persist(instructor);
            announcementEntityManager.flush();
            return new Teacher(person.getId(), jwtService.generateAccessToken(person));
        });
    }

    private Long seedAnnouncementTaughtBy(Teacher teacher) {
        return transactionTemplate.execute(status -> {
            CycleTestFixtures cycleFixtures = new CycleTestFixtures(announcementEntityManager);
            var term = cycleFixtures.term(2100 + (int) (System.nanoTime() % 800));
            var cycle = cycleFixtures.programCycle(cycleFixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = cycleFixtures.section(cycleFixtures.subject(), term, StatusType.ACTIVE, cycle);
            Instructor instructor = announcementEntityManager.createQuery("select i from Instructor i where i.person.id = :personId", Instructor.class)
                    .setParameter("personId", teacher.personId()).getSingleResult();
            cycleFixtures.assignLead(section, instructor);
            Announcement announcement = Announcement.builder()
                    .subjectSection(section)
                    .createdBy(cycleFixtures.person())
                    .title("Aviso")
                    .body("Cuerpo")
                    .category(AnnouncementCategory.REMINDER)
                    .audienceSpec(AudienceSpec.wholeSection())
                    .audienceLabel("Toda la comision")
                    .status(AnnouncementStatus.SENT)
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            announcementEntityManager.persist(announcement);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            announcementEntityManager.persist(institution);
            Student student = new Student();
            student.setPerson(cycleFixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            announcementEntityManager.persist(student);
            announcementEntityManager.persist(AnnouncementRecipient.builder()
                    .announcement(announcement).student(student).readAt(LocalDateTime.now()).build());
            announcementEntityManager.flush();
            return announcement.getId();
        });
    }

    private int recipientsStatusOf(Long announcementId, MatrixRole role) throws Exception {
        return statusOf(HttpMethod.GET, "/announcements/" + announcementId + "/recipients", null, role);
    }

    private int recipientsStatusWithToken(Long announcementId, String token) throws Exception {
        var response = mockMvc.perform(request(HttpMethod.GET, "/announcements/" + announcementId + "/recipients")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        lastResponseBody = response.getContentAsString();
        return response.getStatus();
    }

    private boolean lastResponseCarriesReadTimes() {
        return lastResponseBody.contains("\"readAt\":\"");
    }

    private void assertReadReceiptsFor(Long announcementId, String token, String label) throws Exception {
        assertThat(recipientsStatusWithToken(announcementId, token)).as(label + " " + lastResponseBody).isEqualTo(200);
        assertThat(lastResponseCarriesReadTimes()).as(label + " read receipts " + lastResponseBody).isTrue();
    }

    private void assertRecipientsFor(Long announcementId, String token, String label) throws Exception {
        assertThat(recipientsStatusWithToken(announcementId, token)).as(label + " " + lastResponseBody).isEqualTo(200);
        assertThat(lastResponseBody).as(label + " recipients").contains("\"file\"");
        assertThat(lastResponseCarriesReadTimes()).as(label + " recipients without read times " + lastResponseBody).isFalse();
    }

    private void assertOldRoleRules(String mode, Long announcementId, Teacher owner, Teacher stranger) throws Exception {
        assertReadReceiptsFor(announcementId, fixtures.tokenFor(MatrixRole.PRECEPTOR), mode + " preceptor");
        assertReadReceiptsFor(announcementId, fixtures.tokenFor(MatrixRole.DIRECTOR), mode + " director");
        assertRecipientsFor(announcementId, owner.token(), mode + " docente of the section");
        assertThat(recipientsStatusWithToken(announcementId, stranger.token())).as(mode + " docente of another section").isEqualTo(404);
    }

    @Test
    void recipientsBranchFollowsTheOldRoleRulesInLegacyMode() throws Exception {
        Teacher owner = teacherWithRoles("DOCENTE");
        Teacher stranger = teacherWithRoles("DOCENTE");
        assertOldRoleRules("LEGACY", seedAnnouncementTaughtBy(owner), owner, stranger);
    }

    @Test
    void recipientsBranchFollowsTheOldRoleRulesInShadowModeEvenWithTheReceiptsGrantedToDocente() throws Exception {
        setPilotMode("SHADOW");
        Teacher owner = teacherWithRoles("DOCENTE");
        Teacher stranger = teacherWithRoles("DOCENTE");
        Long announcementId = seedAnnouncementTaughtBy(owner);
        assertOldRoleRules("SHADOW", announcementId, owner, stranger);

        setGrant("DOCENTE", "announcement.view_read_receipts", true);

        assertOldRoleRules("SHADOW granted", announcementId, owner, stranger);
    }

    @Test
    void recipientsBranchFollowsTheReadReceiptsPermissionInEnforceMode() throws Exception {
        setPilotMode("ENFORCE");
        Teacher owner = teacherWithRoles("DOCENTE");
        Teacher stranger = teacherWithRoles("DOCENTE");
        Long announcementId = seedAnnouncementTaughtBy(owner);
        assertOldRoleRules("ENFORCE default", announcementId, owner, stranger);

        setGrant("DOCENTE", "announcement.view_read_receipts", true);

        assertReadReceiptsFor(announcementId, owner.token(), "docente of the section with the permission");
        assertThat(recipientsStatusWithToken(announcementId, stranger.token())).as("docente of another section with the permission " + lastResponseBody).isEqualTo(404);
        assertReadReceiptsFor(announcementId, fixtures.tokenFor(MatrixRole.PRECEPTOR), "preceptor");

        setGrant("PRECEPTOR", "announcement.view_read_receipts", false);

        assertThat(recipientsStatusOf(announcementId, MatrixRole.PRECEPTOR)).as("preceptor without the permission goes through the instructor check " + lastResponseBody).isEqualTo(404);
    }

    @Test
    void userWithDocenteAndPreceptorRolesGetsReadReceiptsInLegacyAndEnforceModes() throws Exception {
        Teacher teacherAndStaff = teacherWithRoles("DOCENTE", "PRECEPTOR");
        Long announcementId = seedAnnouncementTaughtBy(teacherWithRoles("DOCENTE"));
        assertReadReceiptsFor(announcementId, teacherAndStaff.token(), "LEGACY");

        setPilotMode("ENFORCE");

        assertReadReceiptsFor(announcementId, teacherAndStaff.token(), "ENFORCE");
    }

    private record SeededSection(Long sectionId, Long announcementId) {
    }

    private SeededSection seedSectionWithoutInstructorAndOneAnnouncement() {
        return transactionTemplate.execute(status -> {
            CycleTestFixtures cycleFixtures = new CycleTestFixtures(announcementEntityManager);
            var term = cycleFixtures.term(2100 + (int) (System.nanoTime() % 800));
            var cycle = cycleFixtures.programCycle(cycleFixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = cycleFixtures.section(cycleFixtures.subject(), term, StatusType.ACTIVE, cycle);
            Announcement announcement = Announcement.builder()
                    .subjectSection(section)
                    .createdBy(cycleFixtures.person())
                    .title("Aviso")
                    .body("Cuerpo")
                    .category(AnnouncementCategory.REMINDER)
                    .audienceSpec(AudienceSpec.wholeSection())
                    .audienceLabel("Toda la comision")
                    .status(AnnouncementStatus.SENT)
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            announcementEntityManager.persist(announcement);
            announcementEntityManager.flush();
            return new SeededSection(section.getId(), announcement.getId());
        });
    }

    private int statusOf(HttpMethod method, String uri, String body, MatrixRole role) throws Exception {
        var builder = request(method, uri).header("Authorization", "Bearer " + fixtures.tokenFor(role));
        if (body != null) {
            builder = builder.contentType(APPLICATION_JSON).content(body);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        lastResponseBody = response.getContentAsString();
        return response.getStatus();
    }

    private void assertDirectorWhoDoesNotTeachTheSectionGetsRealSuccess(String mode) throws Exception {
        SeededSection seeded = seedSectionWithoutInstructorAndOneAnnouncement();
        String section = "/subject-sections/" + seeded.sectionId();
        String createBody = "{\"title\":\"Aviso\",\"body\":\"Cuerpo\",\"category\":\"REMINDER\",\"urgent\":false,\"scheduledFor\":\"2099-01-01T10:00:00\"}";
        String updateBody = "{\"title\":\"Nuevo\",\"body\":\"Cuerpo\",\"category\":\"REMINDER\",\"urgent\":false}";
        String previewBody = "{\"key\":\"ALL\"}";

        assertThat(statusOf(HttpMethod.POST, section + "/announcements", createBody, MatrixRole.DIRECTOR)).as(mode + " create " + lastResponseBody).isEqualTo(201);
        assertThat(statusOf(HttpMethod.GET, section + "/announcements", null, MatrixRole.DIRECTOR)).as(mode + " list").isEqualTo(200);
        assertThat(statusOf(HttpMethod.GET, section + "/audience-segments", null, MatrixRole.DIRECTOR)).as(mode + " segments").isEqualTo(200);
        assertThat(statusOf(HttpMethod.POST, section + "/audience-preview", previewBody, MatrixRole.DIRECTOR)).as(mode + " preview").isEqualTo(200);
        assertThat(statusOf(HttpMethod.PUT, "/announcements/" + seeded.announcementId(), updateBody, MatrixRole.DIRECTOR)).as(mode + " update").isEqualTo(200);
        assertThat(statusOf(HttpMethod.GET, "/announcements/" + seeded.announcementId() + "/recipients", null, MatrixRole.DIRECTOR)).as(mode + " recipients").isEqualTo(200);

        assertThat(statusOf(HttpMethod.POST, section + "/announcements", createBody, MatrixRole.DOCENTE)).as(mode + " docente of another section").isEqualTo(404);
        assertThat(statusOf(HttpMethod.POST, section + "/announcements", createBody, MatrixRole.ALUMNO)).as(mode + " student").isEqualTo(403);

        assertThat(statusOf(HttpMethod.DELETE, "/announcements/" + seeded.announcementId(), null, MatrixRole.DIRECTOR)).as(mode + " delete").isEqualTo(204);
    }

    @Test
    void directorWhoDoesNotTeachTheSectionGetsRealSuccessInLegacyMode() throws Exception {
        assertDirectorWhoDoesNotTeachTheSectionGetsRealSuccess("LEGACY");
    }

    @Test
    void directorWhoDoesNotTeachTheSectionGetsRealSuccessInShadowMode() throws Exception {
        setPilotMode("SHADOW");
        assertDirectorWhoDoesNotTeachTheSectionGetsRealSuccess("SHADOW");
    }

    @Test
    void directorWhoDoesNotTeachTheSectionGetsRealSuccessInEnforceMode() throws Exception {
        setPilotMode("ENFORCE");
        assertDirectorWhoDoesNotTeachTheSectionGetsRealSuccess("ENFORCE");
    }

    @Test
    void directorGetsNotFoundForSectionsAndAnnouncementsThatDoNotExist() throws Exception {
        String createBody = "{\"title\":\"Aviso\",\"body\":\"Cuerpo\",\"category\":\"REMINDER\",\"urgent\":false,\"scheduledFor\":\"2099-01-01T10:00:00\"}";
        String updateBody = "{\"title\":\"Nuevo\",\"body\":\"Cuerpo\",\"category\":\"REMINDER\",\"urgent\":false}";
        String previewBody = "{\"key\":\"ALL\"}";
        for (String kind : List.of("subject-sections", "course-sections")) {
            String section = "/" + kind + "/987654321";
            assertThat(statusOf(HttpMethod.POST, section + "/announcements", createBody, MatrixRole.DIRECTOR)).as(kind + " create " + lastResponseBody).isEqualTo(404);
            assertThat(statusOf(HttpMethod.GET, section + "/announcements", null, MatrixRole.DIRECTOR)).as(kind + " list " + lastResponseBody).isEqualTo(404);
            assertThat(statusOf(HttpMethod.GET, section + "/audience-segments", null, MatrixRole.DIRECTOR)).as(kind + " segments " + lastResponseBody).isEqualTo(404);
            assertThat(statusOf(HttpMethod.POST, section + "/audience-preview", previewBody, MatrixRole.DIRECTOR)).as(kind + " preview " + lastResponseBody).isEqualTo(404);
        }
        assertThat(statusOf(HttpMethod.PUT, "/announcements/987654321", updateBody, MatrixRole.DIRECTOR)).as("update " + lastResponseBody).isEqualTo(404);
        assertThat(statusOf(HttpMethod.DELETE, "/announcements/987654321", null, MatrixRole.DIRECTOR)).as("delete " + lastResponseBody).isEqualTo(404);
        assertThat(statusOf(HttpMethod.GET, "/announcements/987654321/recipients", null, MatrixRole.DIRECTOR)).as("recipients " + lastResponseBody).isEqualTo(404);
    }

    private AuthorizationMatrixRow rowOf(String key) {
        return pilotRows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }
}
