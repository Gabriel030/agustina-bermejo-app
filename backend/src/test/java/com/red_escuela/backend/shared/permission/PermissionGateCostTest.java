package com.red_escuela.backend.shared.permission;

import com.red_escuela.backend.testsupport.NightlyTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

@NightlyTest(reason = "microbenchmark de la compuerta contra hasAnyRole")
class PermissionGateCostTest {

    static class Target {

        @PreAuthorize("hasAnyRole('DOCENTE', 'DIRECTOR')")
        public int legacy() {
            return 1;
        }

        @PreAuthorize("@permissionGate.allows(authentication, 'probe.read', 'ROLE_DOCENTE', 'ROLE_DIRECTOR')")
        public int gated() {
            return 1;
        }

        public int bare() {
            return 1;
        }
    }

    @Configuration
    @EnableMethodSecurity
    @Import({PermissionGate.class, PermissionSnapshotCache.class, InMemoryPermissionPolicyStore.class, LoggingPermissionDiscrepancySink.class})
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        Target target() {
            return new Target();
        }
    }

    @Test
    void reportsNanosPerCall() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var store = context.getBean(InMemoryPermissionPolicyStore.class);
            store.replace(new PermissionSnapshot(Map.of("DOCENTE", Set.of("probe.read")),
                    Map.of(PermissionSnapshot.modeKey("DOCENTE", "probe"), PermissionMode.ENFORCE)));
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("u", "p", AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
            Target target = context.getBean(Target.class);
            int iterations = 2_000_000;
            long sink = 0;
            for (int round = 0; round < 3; round++) {
                long t0 = System.nanoTime();
                for (int i = 0; i < iterations; i++) sink += target.legacy();
                long t1 = System.nanoTime();
                for (int i = 0; i < iterations; i++) sink += target.gated();
                long t2 = System.nanoTime();
                System.out.printf("COST round=%d hasAnyRole=%.0fns gate(ENFORCE)=%.0fns%n", round,
                        (double) (t1 - t0) / iterations, (double) (t2 - t1) / iterations);
            }
            if (sink == 42) System.out.println(sink);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
