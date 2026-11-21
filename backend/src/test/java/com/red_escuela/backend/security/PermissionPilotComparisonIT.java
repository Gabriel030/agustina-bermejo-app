package com.red_escuela.backend.security;

import com.red_escuela.backend.identity.specialist.PermissionDiscrepancyAccumulator;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
    private PermissionDiscrepancyAccumulator discrepancyAccumulator;

    @AfterEach
    void restoreDefaultGrid() {
        jdbcTemplate.update("delete from permission_discrepancy_counter");
        jdbcTemplate.update("delete from permission_module_mode");
        jdbcTemplate.update("delete from role_permission where role = 'DOCENTE' and permission_id = (select id from permission where code = 'announcement.update')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.update'");
        jdbcTemplate.update("delete from role_permission where role in ('PRECEPTOR', 'ADMINISTRATIVO') and permission_id in (select id from permission where code in ('announcement.update', 'audit.read'))");
        discrepancyAccumulator.flush();
        jdbcTemplate.update("delete from permission_discrepancy_counter");
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

    private boolean isDenied(String key, String body, MatrixRole role) throws Exception {
        int status = statusOf(endpointsByKey().get(key), body == null ? "" : body, role);
        return status == 403 || (role == null && status == 401);
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = pilotRows();
        assertThat(rows).hasSize(14);
        List<String> mismatches = new java.util.ArrayList<>();
        for (AuthorizationMatrixRow row : rows) {
            assertThat(endpointsByKey().get(row.key())).as(row.key()).isNotNull();
            for (MatrixRole role : MatrixRole.values()) {
                boolean legacyDenied = row.byRole().get(role) == AccessExpectation.DENY;
                boolean expectedDenied = legacyDenied;
                boolean denied = isDenied(row.key(), row.body(), role);
                if (denied != expectedDenied) {
                    mismatches.add(mode + " " + role + " " + row.key() + " esperado denegado=" + expectedDenied + " obtenido denegado=" + denied);
                }
            }
            if (!isDenied(row.key(), row.body(), null)) {
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
    void directorCanManageAnnouncementsInEveryModeAndStudentCannot() throws Exception {
        for (String mode : List.of("LEGACY", "SHADOW", "ENFORCE")) {
            jdbcTemplate.update("delete from permission_module_mode");
            if (!mode.equals("LEGACY")) {
                setPilotMode(mode);
            } else {
                cache.invalidate();
            }
            for (AuthorizationMatrixRow row : pilotRows()) {
                if (row.key().equals(AUDIT_LOG_KEY)) {
                    continue;
                }
                assertThat(isDenied(row.key(), row.body(), MatrixRole.DIRECTOR)).as(mode + " DIRECTOR " + row.key()).isFalse();
                assertThat(isDenied(row.key(), row.body(), MatrixRole.ALUMNO)).as(mode + " ALUMNO " + row.key()).isTrue();
            }
        }
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsRevokedOrGranted() throws Exception {
        setPilotMode("ENFORCE");
        String updateKey = "PUT /announcements/{id}";
        String updateBody = rowOf(updateKey).body();
        assertThat(isDenied(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isDenied(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isDenied(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isTrue();

        setGrant("DOCENTE", "announcement.update", false);
        setGrant("PRECEPTOR", "announcement.update", true);
        setGrant("ADMINISTRATIVO", "audit.read", true);

        assertThat(isDenied(updateKey, updateBody, MatrixRole.DOCENTE)).isTrue();
        assertThat(isDenied("DELETE /announcements/{id}", "", MatrixRole.DOCENTE)).isFalse();
        assertThat(isDenied(updateKey, updateBody, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isDenied(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isFalse();

        setGrant("DOCENTE", "announcement.update", true);
        assertThat(isDenied(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
    }

    @Test
    void shadowModeKeepsTheOldDecisionAndCountsTheGridDivergence() throws Exception {
        setPilotMode("SHADOW");
        String updateKey = "PUT /announcements/{id}";
        String updateBody = rowOf(updateKey).body();

        setGrant("DOCENTE", "announcement.update", false);
        setGrant("PRECEPTOR", "announcement.update", true);

        assertThat(isDenied(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isDenied(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
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

        assertThat(isDenied(updateKey, updateBody, MatrixRole.DOCENTE)).isFalse();
        assertThat(isDenied(updateKey, updateBody, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isDenied(AUDIT_LOG_KEY, "", MatrixRole.ADMINISTRATIVO)).isTrue();
        discrepancyAccumulator.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from permission_discrepancy_counter", Integer.class)).isZero();
    }

    private AuthorizationMatrixRow rowOf(String key) {
        return pilotRows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }
}
