package com.red_escuela.backend.shared.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("gate-probe")
@WebMvcTest(GateProbeController.class)
@Import({PermissionGate.class, PermissionSnapshotCache.class, InMemoryPermissionPolicyStore.class,
        GateProbeService.class, GateProbeController.class, PermissionGateWebTest.ProbeConfig.class})
class PermissionGateWebTest {

    static final Set<String> ALL_CODES = Set.of("probe.read", "probe.single", "probe.usecase", "probe.meta", "probe.other");

    record Discrepancy(String role, String permission, boolean legacy, boolean table) {
    }

    static class CapturingSink implements PermissionDiscrepancySink {
        final List<Discrepancy> recorded = new ArrayList<>();

        @Override
        public void record(String role, String permission, boolean legacyDecision, boolean permissionDecision) {
            recorded.add(new Discrepancy(role, permission, legacyDecision, permissionDecision));
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ProbeApplication {
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class ProbeConfig {

        @Bean
        static AnnotationTemplateExpressionDefaults templateDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        CapturingSink capturingSink() {
            return new CapturingSink();
        }

        @Bean
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        SecurityFilterChain probeChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    InMemoryPermissionPolicyStore store;
    @Autowired
    PermissionSnapshotCache cache;
    @Autowired
    CapturingSink sink;
    @Autowired
    MutableClock clock;

    @BeforeEach
    void reset() {
        store.replace(PermissionSnapshot.EMPTY);
        cache.invalidate();
        sink.recorded.clear();
        clock.now = Instant.parse("2026-01-01T00:00:00Z");
    }

    private void configure(PermissionMode mode, String role, String module, String... permissions) {
        store.replace(new PermissionSnapshot(
                Map.of(role, Set.of(permissions)),
                Map.of(PermissionSnapshot.modeKey(role, module), mode),
                ALL_CODES));
        cache.invalidate();
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void legacyModeAllowsListedRoleOnEveryLayer() throws Exception {
        for (String path : List.of("/probe/controller", "/probe/meta")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void legacyModeDeniesRoleNotListed() throws Exception {
        mockMvc.perform(get("/probe/single")).andExpect(status().isForbidden());
        mockMvc.perform(get("/probe/usecase")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void legacyModeAllowsUseCaseLayer() throws Exception {
        mockMvc.perform(get("/probe/usecase")).andExpect(status().isOk()).andExpect(content().string("usecase-ok"));
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void legacyModeMatchesPlainHasAnyRoleBehavior() throws Exception {
        mockMvc.perform(get("/probe/legacy")).andExpect(status().isOk());
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void shadowModeKeepsLegacyDecisionAndRecordsDiscrepancy() throws Exception {
        configure(PermissionMode.SHADOW, "DOCENTE", "probe", "probe.other");
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        assertThat(sink.recorded).containsExactly(new Discrepancy("DOCENTE", "probe.read", true, false));
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void shadowModeDoesNotRecordWhenBothAgree() throws Exception {
        configure(PermissionMode.SHADOW, "DOCENTE", "probe", "probe.read");
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        assertThat(sink.recorded).isEmpty();
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void shadowModeStillDeniesWhenLegacyDeniesAndRecordsExtraGrant() throws Exception {
        configure(PermissionMode.SHADOW, "DOCENTE", "probe", "probe.single");
        mockMvc.perform(get("/probe/single")).andExpect(status().isForbidden());
        assertThat(sink.recorded).containsExactly(new Discrepancy("DOCENTE", "probe.single", false, true));
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void enforceModeDeniesWhenTableLacksPermissionDespiteLegacyRole() throws Exception {
        configure(PermissionMode.ENFORCE, "DOCENTE", "probe", "probe.other");
        mockMvc.perform(get("/probe/controller")).andExpect(status().isForbidden());
        mockMvc.perform(get("/probe/meta")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void enforceModeAllowsWhenTableGrantsPermissionDespiteLegacyDeny() throws Exception {
        configure(PermissionMode.ENFORCE, "DOCENTE", "probe", "probe.single", "probe.usecase");
        mockMvc.perform(get("/probe/single")).andExpect(status().isOk());
        mockMvc.perform(get("/probe/usecase")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void enforceModeIsScopedToTheModuleOfTheRole() throws Exception {
        configure(PermissionMode.ENFORCE, "DOCENTE", "other", "other.read");
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ALUMNO")
    void userWithoutListedRoleGets403InEveryMode() throws Exception {
        for (PermissionMode mode : PermissionMode.values()) {
            configure(mode, "ALUMNO", "probe");
            mockMvc.perform(get("/probe/controller")).andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/probe/controller")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/probe/usecase")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void snapshotIsCachedUntilTtlExpires() throws Exception {
        int base = store.loadCount();
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        assertThat(store.loadCount()).isEqualTo(base + 1);
        clock.now = clock.now.plus(Duration.ofSeconds(29));
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        assertThat(store.loadCount()).isEqualTo(base + 1);
        clock.now = clock.now.plus(Duration.ofSeconds(2));
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        assertThat(store.loadCount()).isEqualTo(base + 2);
    }

    @Test
    @WithMockUser(roles = "DOCENTE")
    void staleSnapshotServedWithinTtlUntilInvalidated() throws Exception {
        configure(PermissionMode.ENFORCE, "DOCENTE", "probe", "probe.read");
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        store.replace(new PermissionSnapshot(Map.of("DOCENTE", Set.of()), Map.of(PermissionSnapshot.modeKey("DOCENTE", "probe"), PermissionMode.ENFORCE), ALL_CODES));
        mockMvc.perform(get("/probe/controller")).andExpect(status().isOk());
        cache.invalidate();
        mockMvc.perform(get("/probe/controller")).andExpect(status().isForbidden());
    }
}
