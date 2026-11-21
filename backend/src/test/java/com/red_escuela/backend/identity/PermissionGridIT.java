package com.red_escuela.backend.identity;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "gate-probe"})
@Import(PermissionGridIT.ProbeConfig.class)
class PermissionGridIT extends AbstractIntegrationTest {

    @RestController
    @Profile("gate-probe")
    static class GridProbeController {

        @GetMapping("/grid-probe/create")
        @RequiresPermission(code = "announcement.create", legacyRoles = "ROLE_ADMINISTRATIVO")
        String create() {
            return "ok";
        }

        @GetMapping("/grid-probe/fixed")
        @RequiresPermission(code = "announcement.fixed_probe", legacyRoles = "ROLE_DIRECTOR,ROLE_DOCENTE")
        String fixed() {
            return "ok";
        }
    }

    @TestConfiguration
    @Import(GridProbeController.class)
    static class ProbeConfig {
    }

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
        jdbcTemplate.update("delete from role_permission where permission_id in (select id from permission where code = 'announcement.fixed_probe')");
        jdbcTemplate.update("delete from permission where code = 'announcement.fixed_probe'");
        jdbcTemplate.update("delete from role_permission where role = 'DOCENTE' and permission_id = (select id from permission where code = 'announcement.create')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.create'");
        cache.invalidate();
    }

    private void setMode(String role, String mode) {
        jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, 'announcement', ?)", role, mode);
        cache.invalidate();
    }

    private int callCreateAs(String role) throws Exception {
        return mockMvc.perform(get("/grid-probe/create").with(user("7").roles(role))).andReturn().getResponse().getStatus();
    }

    private String body(String role, boolean granted, String permissionCode, String reason) {
        return "{\"reason\":\"" + reason + "\",\"changes\":[{\"role\":\"" + role + "\",\"permissionCode\":\""
                + permissionCode + "\",\"granted\":" + granted + "}]}";
    }

    private int updateGrid(String json) throws Exception {
        return mockMvc.perform(put("/permissions/grid").with(user("director-it").roles("DIRECTOR"))
                .contentType(APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
    }

    @Test
    void enforceModeFollowsTheGridAndEditingItChangesTheAccess() throws Exception {
        setMode("DOCENTE", "ENFORCE");
        assertThat(callCreateAs("DOCENTE")).isEqualTo(200);

        assertThat(updateGrid(body("DOCENTE", false, "announcement.create", "Quitar permiso de prueba"))).isEqualTo(200);
        assertThat(callCreateAs("DOCENTE")).isEqualTo(403);

        assertThat(updateGrid(body("DOCENTE", true, "announcement.create", "Devolver permiso de prueba"))).isEqualTo(200);
        assertThat(callCreateAs("DOCENTE")).isEqualTo(200);
    }

    @Test
    void legacyModeIgnoresTheGrid() throws Exception {
        assertThat(callCreateAs("DOCENTE")).isEqualTo(403);
        assertThat(callCreateAs("ADMINISTRATIVO")).isEqualTo(200);
    }

    @Test
    void shadowModeDecidesWithLegacyAndCountsTheDivergence() throws Exception {
        setMode("DOCENTE", "SHADOW");
        assertThat(callCreateAs("DOCENTE")).isEqualTo(403);
        assertThat(callCreateAs("DOCENTE")).isEqualTo(403);

        mockMvc.perform(get("/permissions/discrepancies").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].role").value("DOCENTE"))
                .andExpect(jsonPath("$.items[0].module").value("announcement"))
                .andExpect(jsonPath("$.items[0].permissionCode").value("announcement.create"))
                .andExpect(jsonPath("$.items[0].count").value(2));
    }

    @Test
    void enforceModeStillGrantsTheDirectorEveryPermission() throws Exception {
        setMode("DIRECTOR", "ENFORCE");
        assertThat(callCreateAs("DIRECTOR")).isEqualTo(200);
    }

    @Test
    void fixedPermissionCannotBeEditedAndGridGrantsToOtherRolesAreIgnored() throws Exception {
        jdbcTemplate.update("insert into permission (code, module, description, fixed_to_director) values ('announcement.fixed_probe', 'announcement', 'Permiso fijo de prueba', true)");
        assertThat(updateGrid(body("DOCENTE", true, "announcement.fixed_probe", "Delegar permiso fijo"))).isEqualTo(403);
        mockMvc.perform(put("/permissions/grid").with(user("director-it").roles("DIRECTOR")).contentType(APPLICATION_JSON)
                        .content(body("DOCENTE", true, "announcement.fixed_probe", "Delegar permiso fijo")))
                .andExpect(jsonPath("$.code").value("PERMISSION_FIXED_TO_DIRECTOR"));

        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.fixed_probe'");
        setMode("DOCENTE", "ENFORCE");
        setModeFor("DIRECTOR", "ENFORCE");
        assertThat(mockMvc.perform(get("/grid-probe/fixed").with(user("7").roles("DOCENTE"))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/grid-probe/fixed").with(user("director-it").roles("DIRECTOR"))).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private void setModeFor(String role, String mode) {
        setMode(role, mode);
    }

    @Test
    void noPermissionIsFixedToTheDirectorUntilMoneyModulesAreSeeded() {
        List<String> fixed = jdbcTemplate.queryForList("select code from permission where fixed_to_director order by code", String.class);
        assertThat(fixed).isEmpty();
        Integer delegated = jdbcTemplate.queryForObject("select count(*) from role_permission rp join permission p on p.id = rp.permission_id where p.fixed_to_director", Integer.class);
        assertThat(delegated).isZero();
    }

    @Test
    void directorRowCannotBeEdited() throws Exception {
        assertThat(updateGrid(body("DIRECTOR", false, "announcement.create", "Quitar al Director"))).isEqualTo(422);
    }

    @Test
    void unknownRoleCodeAndDuplicateChangesAreRejectedWith4xx() throws Exception {
        assertThat(updateGrid(body("NOPE", true, "announcement.create", "Rol inexistente"))).isEqualTo(422);
        assertThat(updateGrid(body("DOCENTE", true, "announcement.nope", "Código inexistente"))).isEqualTo(422);
        String duplicated = "{\"reason\":\"Duplicado\",\"changes\":[{\"role\":\"PRECEPTOR\",\"permissionCode\":\"announcement.read\",\"granted\":true},"
                + "{\"role\":\"PRECEPTOR\",\"permissionCode\":\"announcement.read\",\"granted\":false}]}";
        assertThat(updateGrid(duplicated)).isEqualTo(422);
        assertThat(jdbcTemplate.queryForObject("select count(*) from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = 'announcement.read')", Integer.class)).isZero();
    }

    @Test
    void editWritesOneAuditEntryWithTheReasonAndNothingWhenUnchanged() throws Exception {
        assertThat(updateGrid(body("PRECEPTOR", true, "announcement.read", "Habilitar lectura a preceptores"))).isEqualTo(200);
        assertThat(updateGrid(body("PRECEPTOR", true, "announcement.read", "Sin cambios reales"))).isEqualTo(200);
        List<String> reasons = jdbcTemplate.queryForList("select reason from audit_log where action = 'PERMISSION_GRID_CHANGED'", String.class);
        assertThat(reasons).containsExactly("Habilitar lectura a preceptores");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = 'announcement.read')");
        cache.invalidate();
    }

    @Test
    void nonDirectorCannotReadOrEditTheGrid() throws Exception {
        mockMvc.perform(get("/permissions/grid").with(user("7").roles("ADMINISTRATIVO"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/permissions/grid").with(user("7").roles("ADMINISTRATIVO")).contentType(APPLICATION_JSON)
                .content(body("DOCENTE", true, "announcement.read", "Intento"))).andExpect(status().isForbidden());
    }

    @Test
    void gridAndCatalogExposeTheSeedGroupedByModuleWithDirectorHoldingEverything() throws Exception {
        mockMvc.perform(get("/permissions/catalog").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.module=='certificate')].permissions.length()").value(org.hamcrest.Matchers.contains(8)))
                .andExpect(jsonPath("$.modules[?(@.module=='audit')].permissions[0].code").value(org.hamcrest.Matchers.contains("audit.read")));
        mockMvc.perform(get("/permissions/grid").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.roles[?(@.role=='DIRECTOR')].permissionCodes.length()").value(org.hamcrest.Matchers.contains(16)))
                .andExpect(jsonPath("$.roles[?(@.role=='ADMINISTRATIVO')].permissionCodes").isNotEmpty());
    }
}
