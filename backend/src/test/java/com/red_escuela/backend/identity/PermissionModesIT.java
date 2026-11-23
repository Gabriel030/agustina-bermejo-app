package com.red_escuela.backend.identity;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.red_escuela.backend.testsupport.GatePermissionProbeConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "gate-probe"})
@Import(GatePermissionProbeConfiguration.class)
class PermissionModesIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    PermissionSnapshotCache cache;

    @AfterEach
    void restoreGrid() {
        jdbcTemplate.update("delete from permission_discrepancy_counter");
        jdbcTemplate.update("delete from permission_module_mode");
        jdbcTemplate.update("delete from audit_log where action = 'PERMISSION_GRID_CHANGED'");
        jdbcTemplate.update("delete from role_permission where role = 'DOCENTE' and permission_id = (select id from permission where code = 'announcement.create')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.create'");
        cache.invalidate();
    }

    private static String modes(String reason, String... changes) {
        return "{\"reason\":\"" + reason + "\",\"changes\":[" + String.join(",", changes) + "]}";
    }

    private static String change(String role, String module, String mode) {
        return "{\"role\":\"" + role + "\",\"module\":\"" + module + "\",\"mode\":\"" + mode + "\"}";
    }

    private ResultActions putModes(String json) throws Exception {
        return mockMvc.perform(put("/permissions/modes").with(user("director-it").roles("DIRECTOR"))
                .contentType(APPLICATION_JSON).content(json));
    }

    private void setMode(String role, String mode) throws Exception {
        putModes(modes("Cambio de modo de prueba", change(role, "announcement", mode))).andExpect(status().isOk());
    }

    private void setDocenteCreateGrant(boolean granted) throws Exception {
        mockMvc.perform(put("/permissions/grid").with(user("director-it").roles("DIRECTOR")).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Ajuste de prueba\",\"changes\":[{\"role\":\"DOCENTE\",\"permissionCode\":\"announcement.create\",\"granted\":" + granted + "}]}"))
                .andExpect(status().isOk());
    }

    private int call(String path, String role) throws Exception {
        return mockMvc.perform(get(path).with(user("7").roles(role))).andReturn().getResponse().getStatus();
    }

    private ResultActions discrepancies(String query) throws Exception {
        return mockMvc.perform(get("/permissions/discrepancies" + query).with(user("director-it").roles("DIRECTOR")));
    }

    @Test
    void enforceViaHttpFollowsTheGridAndGridEditsChangeTheAccess() throws Exception {
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(403);

        setMode("DOCENTE", "ENFORCE");
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(200);

        setDocenteCreateGrant(false);
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(403);

        setDocenteCreateGrant(true);
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(200);
    }

    @Test
    void shadowViaHttpDecidesWithLegacyAndRecordsLegacyDeniedGridAllowed() throws Exception {
        setMode("DOCENTE", "SHADOW");
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(403);
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(403);

        discrepancies("?module=announcement").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("LEGACY_DENIED_GRID_ALLOWED"))
                .andExpect(jsonPath("$.items[0].count").value(2));
    }

    @Test
    void shadowViaHttpDecidesWithLegacyAndRecordsLegacyAllowedGridDenied() throws Exception {
        setDocenteCreateGrant(false);
        setMode("DOCENTE", "SHADOW");
        assertThat(call("/grid-probe/docente-legacy", "DOCENTE")).isEqualTo(200);

        discrepancies("").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("LEGACY_ALLOWED_GRID_DENIED"))
                .andExpect(jsonPath("$.items[0].count").value(1));
    }

    @Test
    void backToLegacyRestoresTheOldDecisionAndStopsCounting() throws Exception {
        setMode("DOCENTE", "ENFORCE");
        setDocenteCreateGrant(false);
        assertThat(call("/grid-probe/create", "DOCENTE")).isEqualTo(403);
        assertThat(call("/grid-probe/docente-legacy", "DOCENTE")).isEqualTo(403);

        setMode("DOCENTE", "LEGACY");
        assertThat(call("/grid-probe/docente-legacy", "DOCENTE")).isEqualTo(200);
        assertThat(call("/grid-probe/create", "ADMINISTRATIVO")).isEqualTo(200);

        discrepancies("").andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/permissions/grid").with(user("director-it").roles("DIRECTOR")))
                .andExpect(jsonPath("$.modes[?(@.role=='DOCENTE' && @.module=='announcement')].mode").value(contains("LEGACY")));
    }

    @Test
    void responseReturnsTheUpdatedGridAndOnlyDirectorCanChangeModes() throws Exception {
        putModes(modes("Pasar a sombra", change("PRECEPTOR", "audit", "SHADOW"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.modes[?(@.role=='PRECEPTOR' && @.module=='audit')].mode").value(contains("SHADOW")));

        mockMvc.perform(put("/permissions/modes").with(user("7").roles("ADMINISTRATIVO")).contentType(APPLICATION_JSON)
                .content(modes("Intento", change("PRECEPTOR", "audit", "ENFORCE")))).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("select mode from permission_module_mode where role = 'PRECEPTOR' and module = 'audit'", String.class)).isEqualTo("SHADOW");
    }

    @Test
    void invalidModeChangesAreRejectedWith422AndNothingIsSaved() throws Exception {
        putModes(modes("Rol inexistente", change("NOPE", "announcement", "SHADOW")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNKNOWN_PERMISSION_ROLE"));
        putModes(modes("Módulo inexistente", change("DOCENTE", "nope", "SHADOW")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNKNOWN_PERMISSION_MODULE"));
        putModes(modes("Duplicado", change("DOCENTE", "announcement", "SHADOW"), change("DOCENTE", "announcement", "ENFORCE")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DUPLICATE_PERMISSION_MODE_CHANGE"));
        putModes(modes("Director", change("DIRECTOR", "announcement", "LEGACY")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DIRECTOR_PERMISSIONS_NOT_EDITABLE"));
        putModes(modes("Válido y luego inválido", change("PRECEPTOR", "announcement", "SHADOW"), change("NOPE", "announcement", "SHADOW")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("select count(*) from permission_module_mode where not (role = 'SUBDIRECTOR' and module = 'document')", Integer.class)).isZero();
    }

    @Test
    void invalidBodiesAreRejectedWith400() throws Exception {
        String valid = change("DOCENTE", "announcement", "SHADOW");
        putModes(modes("", valid)).andExpect(status().isBadRequest());
        putModes(modes("x".repeat(500), valid)).andExpect(status().isOk());
        putModes(modes("x".repeat(501), valid)).andExpect(status().isBadRequest());
        putModes(modes("Sin cambios")).andExpect(status().isBadRequest());
        String manyChanges = String.join(",", java.util.Collections.nCopies(201, valid));
        putModes(modes("Demasiados", manyChanges)).andExpect(status().isBadRequest());
        putModes(modes("Rol largo", change("R".repeat(51), "announcement", "SHADOW"))).andExpect(status().isBadRequest());
        putModes(modes("Módulo largo", change("DOCENTE", "m".repeat(51), "SHADOW"))).andExpect(status().isBadRequest());
        putModes(modes("Modo inválido", change("DOCENTE", "announcement", "NOPE"))).andExpect(status().isBadRequest());
    }

    @Test
    void modeChangeWritesOneAuditEntryWithRoleModuleModesAndReasonAndNothingWhenUnchanged() throws Exception {
        putModes(modes("Pasar a sombra", change("DOCENTE", "announcement", "SHADOW"))).andExpect(status().isOk());
        putModes(modes("Sin cambio real", change("DOCENTE", "announcement", "SHADOW"), change("PRECEPTOR", "audit", "LEGACY")))
                .andExpect(status().isOk());
        putModes(modes("Volver a Legado", change("DOCENTE", "announcement", "LEGACY"))).andExpect(status().isOk());

        List<java.util.Map<String, Object>> entries = jdbcTemplate.queryForList(
                "select before_value, after_value, reason from audit_log where action = 'PERMISSION_GRID_CHANGED' order by id");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("before_value", "Cambio de modo DOCENTE/announcement: LEGACY")
                .containsEntry("after_value", "Cambio de modo DOCENTE/announcement: SHADOW")
                .containsEntry("reason", "Pasar a sombra");
        assertThat(entries.get(1)).containsEntry("before_value", "Cambio de modo DOCENTE/announcement: SHADOW")
                .containsEntry("after_value", "Cambio de modo DOCENTE/announcement: LEGACY")
                .containsEntry("reason", "Volver a Legado");
    }

    @Test
    void discrepanciesFilterByModuleValidatesItAndSummaryAggregatesByModuleAndDirection() throws Exception {
        setMode("DOCENTE", "SHADOW");
        call("/grid-probe/create", "DOCENTE");
        call("/grid-probe/create", "DOCENTE");
        setDocenteCreateGrant(false);
        call("/grid-probe/docente-legacy", "DOCENTE");

        discrepancies("").andExpect(jsonPath("$.totalElements").value(2));
        discrepancies("?module=announcement").andExpect(jsonPath("$.totalElements").value(2));
        discrepancies("?module=audit").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        discrepancies("?module=nope").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PERMISSION_MODULE"));
        discrepancies("?size=1000").andExpect(jsonPath("$.size").value(100));

        mockMvc.perform(get("/permissions/discrepancies/summary").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].module").value("announcement"))
                .andExpect(jsonPath("$.modules[0].legacyDeniedGridAllowed").value(2))
                .andExpect(jsonPath("$.modules[0].legacyAllowedGridDenied").value(1))
                .andExpect(jsonPath("$.modules[0].total").value(3))
                .andExpect(jsonPath("$.modules[0].lastSeenAt").isNotEmpty());
        mockMvc.perform(get("/permissions/discrepancies/summary").with(user("7").roles("ADMINISTRATIVO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void summaryIsEmptyWhenThereAreNoDiscrepancies() throws Exception {
        mockMvc.perform(get("/permissions/discrepancies/summary").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modules.length()").value(0));
    }
}
