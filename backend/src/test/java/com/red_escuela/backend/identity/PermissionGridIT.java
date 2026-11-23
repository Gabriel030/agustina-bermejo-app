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
@Import(GatePermissionProbeConfiguration.class)
class PermissionGridIT extends AbstractIntegrationTest {

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
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where code in ('audit.read', 'announcement.create'))");
        jdbcTemplate.update("delete from audit_log where action in ('PERMISSION_GRID_CHANGED', 'PERMISSION_SENSITIVE_CHANGED')");
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
                .andExpect(jsonPath("$.items[0].direction").value("LEGACY_DENIED_GRID_ALLOWED"))
                .andExpect(jsonPath("$.items[0].count").value(2));
    }

    @Test
    void enforceModeStillGrantsTheDirectorEveryPermission() throws Exception {
        setMode("DIRECTOR", "ENFORCE");
        assertThat(callCreateAs("DIRECTOR")).isEqualTo(200);
    }

    private String sensitiveBody(String role, boolean granted, boolean confirmed) {
        return "{\"reason\":\"Delegar auditoria por ausencia\",\"sensitiveConfirmed\":" + confirmed
                + ",\"changes\":[{\"role\":\"" + role + "\",\"permissionCode\":\"audit.read\",\"granted\":" + granted + "}]}";
    }

    private int readAuditLogAs(String role) throws Exception {
        return mockMvc.perform(get("/audit-log").with(user("7").roles(role))).andReturn().getResponse().getStatus();
    }

    private void enforceAuditModule() {
        jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values ('ADMINISTRATIVO', 'audit', 'ENFORCE')");
        jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values ('DIRECTOR', 'audit', 'ENFORCE')");
        cache.invalidate();
    }

    @Test
    void delegatingSensitiveAuditReadOpensAccessAndRevokingItClosesAgain() throws Exception {
        enforceAuditModule();
        assertThat(readAuditLogAs("ADMINISTRATIVO")).isEqualTo(403);

        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", true, true))).isEqualTo(200);
        assertThat(readAuditLogAs("ADMINISTRATIVO")).isEqualTo(200);
        assertThat(readAuditLogAs("DIRECTOR")).isEqualTo(200);

        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", false, true))).isEqualTo(200);
        assertThat(readAuditLogAs("ADMINISTRATIVO")).isEqualTo(403);
        assertThat(readAuditLogAs("DIRECTOR")).isEqualTo(200);
    }

    @Test
    void sensitiveChangeWithoutConfirmationIsRejectedAndChangesNothing() throws Exception {
        enforceAuditModule();
        mockMvc.perform(put("/permissions/grid").with(user("director-it").roles("DIRECTOR")).contentType(APPLICATION_JSON)
                        .content(sensitiveBody("ADMINISTRATIVO", true, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SENSITIVE_PERMISSION_CONFIRMATION_REQUIRED"));
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", true, false).replace("\"sensitiveConfirmed\":false,", ""))).isEqualTo(422);
        assertThat(readAuditLogAs("ADMINISTRATIVO")).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where action = 'PERMISSION_SENSITIVE_CHANGED'", Integer.class)).isZero();
    }

    @Test
    void revokingSensitivePermissionWithoutConfirmationIsRejectedAndChangesNothing() throws Exception {
        enforceAuditModule();
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", true, true))).isEqualTo(200);
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", false, false))).isEqualTo(422);
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", false, false).replace("\"sensitiveConfirmed\":false,", ""))).isEqualTo(422);
        assertThat(readAuditLogAs("ADMINISTRATIVO")).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where action = 'PERMISSION_SENSITIVE_CHANGED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void unconfirmedSensitiveChangeRejectsTheWholeRequestIncludingNonSensitiveChanges() throws Exception {
        String mixed = "{\"reason\":\"Cambio mixto\",\"changes\":[{\"role\":\"PRECEPTOR\",\"permissionCode\":\"announcement.read\",\"granted\":true},"
                + "{\"role\":\"ADMINISTRATIVO\",\"permissionCode\":\"audit.read\",\"granted\":true}]}";
        assertThat(updateGrid(mixed)).isEqualTo(422);
        assertThat(jdbcTemplate.queryForObject("select count(*) from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = 'announcement.read')", Integer.class)).isZero();
    }

    @Test
    void sensitiveChangeIsAuditedAsHighlightedEventWithRoleBeforeAfterAndReason() throws Exception {
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", true, true))).isEqualTo(200);
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", true, true))).isEqualTo(200);
        assertThat(updateGrid(sensitiveBody("ADMINISTRATIVO", false, true))).isEqualTo(200);
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select before_value, after_value, reason, actor_role from audit_log where action = 'PERMISSION_SENSITIVE_CHANGED' order by id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("before_value")).isEqualTo("audit.read:ADMINISTRATIVO:DENIED");
        assertThat(rows.get(0).get("after_value")).isEqualTo("audit.read:ADMINISTRATIVO:GRANTED");
        assertThat(rows.get(0).get("reason")).isEqualTo("Delegar auditoria por ausencia");
        assertThat(rows.get(0).get("actor_role")).isEqualTo("DIRECTOR");
        assertThat(rows.get(1).get("after_value")).isEqualTo("audit.read:ADMINISTRATIVO:DENIED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where action = 'PERMISSION_GRID_CHANGED'", Integer.class)).isZero();
    }

    @Test
    void nonSensitivePermissionStillEditsWithoutConfirmation() throws Exception {
        assertThat(updateGrid(body("PRECEPTOR", true, "announcement.read", "Sin confirmacion reforzada"))).isEqualTo(200);
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id = (select id from permission where code = 'announcement.read')");
        cache.invalidate();
    }

    @Test
    void nonDirectorCannotDelegateSensitivePermissions() throws Exception {
        int status = mockMvc.perform(put("/permissions/grid").with(user("8").roles("ADMINISTRATIVO"))
                .contentType(APPLICATION_JSON).content(sensitiveBody("ADMINISTRATIVO", true, true))).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("select count(*) from role_permission where role = 'ADMINISTRATIVO' and permission_id = (select id from permission where code = 'audit.read')", Integer.class)).isZero();
    }

    @Test
    void theSeededSensitivePermissionsAreExactlyTheCriticalOnesOfEachMigratedModule() throws Exception {
        assertThat(jdbcTemplate.queryForList("select code from permission where sensitive", String.class)).containsExactlyInAnyOrder("attendance.cancel_class_session", "attendance.justify", "attendance.review_change_request", "attendance.review_justification", "audit.read", "dataimport.commit", "dataimport.rollback", "dataimport.seal", "identity.create_staff", "identity.update_staff", "identity.withdraw_staff", "identity.reactivate_staff", "identity.reset_staff_password", "identity.withdraw_student", "identity.reactivate_student", "identity.reset_student_password", "platform.change_payment_methods", "platform.change_whatsapp_settings", "notification.confirm_broadcast", "notification.cancel_broadcast", "notification.send_invitation_wave", "document.debt-exception.issue", "notification.create_message_template", "notification.update_message_template", "notification.deactivate_message_template", "notification.create_whatsapp_template", "notification.update_whatsapp_template", "graduation.approve_request", "graduation.reject_request", "graduation.reissue_certificate", "certificate.revoke", "certificate.issue", "certificate.templates_manage", "certificate.signers_manage", "certificate.document_settings_manage", "certificate.regular_settings_manage", "certificate.exceptions_create_direct", "certificate.exceptions_approve", "certificate.exceptions_reject", "certificate.exam_issue_batch", "enrollment.change_subject_section", "enrollment.withdraw_subject", "enrollment.withdraw_enrollment", "enrollment.advance_billing_year", "enrollment.create_ad_hoc_invoice", "enrollment.grant_enrollment_fee_exemption", "enrollment.revoke_enrollment_fee_exemption", "enrollment.manage_waitlist", "enrollment.review_program_application", "evaluation.review_section_evaluation_config", "evaluation.publish_grades", "evaluation.record_exam_recovery", "evaluation.correct_published_grade", "evaluation.close_exam_board", "evaluation.record_exam_board_final_grades", "evaluation.confirm_commission_closure", "evaluation.approve_commission_closure", "evaluation.reopen_commission_closure", "evaluation.manage_closure_exceptions", "evaluation.cancel_exam_board", "academicstructure.review_offering_cycle", "academicstructure.review_section_modification", "academicstructure.change_program_status", "academicstructure.change_course_status", "academicstructure.change_catalog_visibility", "academicstructure.delete_holiday", "academicstructure.review_subject", "academicstructure.change_subject_status", "academicstructure.change_section_status", "academicstructure.remove_section_instructor", "academicstructure.change_term_status", "academicstructure.manage_term_enrollment_window", "billing.register_cash_payment", "billing.register_annual_upfront_cash_payment", "billing.submit_bank_transfer_receipt", "billing.review_bank_transfer_receipt", "billing.revoke_payment_approval", "billing.register_chargeback", "billing.reverse_chargeback", "billing.request_refund", "billing.complete_refund", "billing.fail_refund", "billing.issue_credit_note", "billing.cancel_invoice", "billing.allocate_account_credit", "billing.run_billing", "billing.issue_monthly_invoices_manually", "billing.retry_draft_invoice_items", "billing.approve_draft_invoices", "billing.discard_draft_invoices", "billing.manage_discount_definition", "billing.grant_student_discount", "billing.revoke_student_discount", "billing.manage_promotion", "billing.propose_price_change", "billing.cancel_price_version", "billing.manage_payment_plan_options", "billing.manage_year_billing_template", "billing.manage_year_billing_cohorts", "billing.set_late_payment_settings", "billing.manage_payment_gateway", "billing.decide_authorization_request", "billing.withdraw_authorization_request", "billing.request_advance_enrollment_fee", "billing.publish_annual_upfront_terms");
        mockMvc.perform(get("/permissions/catalog").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.module=='audit')].permissions[0].sensitive").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.modules[?(@.module=='audit')].permissions[0].fixedToDirector").value(org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void gridGrantsOfPreviouslyFixedPermissionsAreHonoredByTheGate() throws Exception {
        jdbcTemplate.update("insert into permission (code, module, description, fixed_to_director) values ('announcement.fixed_probe', 'announcement', 'Permiso de prueba', true)");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.fixed_probe'");
        setMode("DOCENTE", "ENFORCE");
        assertThat(mockMvc.perform(get("/grid-probe/fixed").with(user("7").roles("DOCENTE"))).andReturn().getResponse().getStatus()).isEqualTo(200);
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
                .andExpect(jsonPath("$.modules[?(@.module=='certificate')].permissions.length()").value(org.hamcrest.Matchers.contains(25)))
                .andExpect(jsonPath("$.modules[?(@.module=='audit')].permissions[0].code").value(org.hamcrest.Matchers.contains("audit.read")));
        mockMvc.perform(get("/permissions/grid").with(user("director-it").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.roles[?(@.role=='DIRECTOR')].permissionCodes.length()").value(org.hamcrest.Matchers.contains(jdbcTemplate.queryForObject("select count(*) from permission", Integer.class))))
                .andExpect(jsonPath("$.roles[?(@.role=='ADMINISTRATIVO')].permissionCodes").isNotEmpty());
    }

    @Test
    void subdirectorReadsCatalogAndGridAsNonEditableAndCannotWrite() throws Exception {
        mockMvc.perform(get("/permissions/catalog").with(user("8").roles("SUBDIRECTOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/permissions/grid").with(user("8").roles("SUBDIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.roles[?(@.role=='SUBDIRECTOR')].permissionCodes.length()").value(org.hamcrest.Matchers.contains(0)))
                .andExpect(jsonPath("$.modes[?(@.role=='SUBDIRECTOR' && @.module=='audit')].mode").value(org.hamcrest.Matchers.contains("LEGACY")));
        mockMvc.perform(put("/permissions/grid").with(user("8").roles("SUBDIRECTOR")).contentType(APPLICATION_JSON)
                .content(body("DOCENTE", true, "announcement.read", "Intento"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/permissions/modes").with(user("8").roles("SUBDIRECTOR")).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"x\",\"changes\":[{\"role\":\"DOCENTE\",\"module\":\"announcement\",\"mode\":\"SHADOW\"}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/permissions/discrepancies").with(user("8").roles("SUBDIRECTOR"))).andExpect(status().isForbidden());
    }

    @Test
    void enforceModeLetsExpandedSubdirectorPassOnlyThroughTheAdministrativeGrant() throws Exception {
        setMode("ADMINISTRATIVO", "ENFORCE");
        assertThat(callAsAuthorities("ADMINISTRATIVO", "SUBDIRECTOR")).isEqualTo(403);

        assertThat(updateGrid(body("DOCENTE", true, "announcement.create", "Permiso solo para docente"))).isEqualTo(200);
        assertThat(callAsAuthorities("ADMINISTRATIVO", "SUBDIRECTOR")).isEqualTo(403);

        assertThat(updateGrid(body("ADMINISTRATIVO", true, "announcement.create", "Permiso para administrativo"))).isEqualTo(200);
        assertThat(callAsAuthorities("ADMINISTRATIVO", "SUBDIRECTOR")).isEqualTo(200);
    }

    private int callAsAuthorities(String... roles) throws Exception {
        return mockMvc.perform(get("/grid-probe/create").with(user("8").roles(roles))).andReturn().getResponse().getStatus();
    }
}
