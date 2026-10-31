package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.platform.api.RequiresModule;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleGuardAspectTest {

    static class MethodGuardedJob {
        final List<String> runs = new ArrayList<>();

        @RequiresModule(RequiresModule.BILLING)
        public void guardedRun() {
            runs.add("guarded");
        }

        @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
        public void fullOnlyRun() {
            runs.add("fullOnly");
        }

        public void plainRun() {
            runs.add("plain");
        }
    }

    @RequiresModule(RequiresModule.BILLING)
    static class ClassGuardedListener {
        final List<String> events = new ArrayList<>();

        public void on(String event) {
            events.add(event);
        }
    }

    private static ModuleGuardAspect aspectWithBillingIn(BillingMode mode) {
        FeatureSnapshotHolder holder = Mockito.mock(FeatureSnapshotHolder.class);
        Mockito.when(holder.current()).thenReturn(new FeatureSnapshot(
                Map.of(FeatureCode.BILLING, FeatureState.billing(mode, 0)), Instant.parse("2026-10-31T12:00:00Z")));
        return new ModuleGuardAspect(new ModuleGuard(new BillingModeGate(holder)));
    }

    private static <T> T proxyOf(T target, ModuleGuardAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void guardedJobDoesNotRunWhenBillingIsOff() {
        MethodGuardedJob job = new MethodGuardedJob();
        MethodGuardedJob proxy = proxyOf(job, aspectWithBillingIn(BillingMode.OFF));

        proxy.guardedRun();
        proxy.fullOnlyRun();
        proxy.plainRun();

        assertThat(job.runs).containsExactly("plain");
    }

    @Test
    void mirrorRunsDefaultGuardedJobButSkipsFullOnlyOne() {
        MethodGuardedJob job = new MethodGuardedJob();
        MethodGuardedJob proxy = proxyOf(job, aspectWithBillingIn(BillingMode.MIRROR));

        proxy.guardedRun();
        proxy.fullOnlyRun();

        assertThat(job.runs).containsExactly("guarded");
    }

    @Test
    void fullRunsEveryGuardedJob() {
        MethodGuardedJob job = new MethodGuardedJob();
        MethodGuardedJob proxy = proxyOf(job, aspectWithBillingIn(BillingMode.FULL));

        proxy.guardedRun();
        proxy.fullOnlyRun();

        assertThat(job.runs).containsExactly("guarded", "fullOnly");
    }

    @Test
    void classLevelGuardSkipsEveryListenerMethodWhenBillingIsOff() {
        ClassGuardedListener listener = new ClassGuardedListener();

        proxyOf(listener, aspectWithBillingIn(BillingMode.OFF)).on("event");
        assertThat(listener.events).isEmpty();

        proxyOf(listener, aspectWithBillingIn(BillingMode.FULL)).on("event");
        assertThat(listener.events).containsExactly("event");
    }
}
