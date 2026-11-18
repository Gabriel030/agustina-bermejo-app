package com.red_escuela.backend.platform.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.platform.api.FeatureQuery;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.exception.ModuleNotEnabledException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.module.RequiresModule;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ModuleGuardAnalyticsTest {

    private static final RequiresModule ANALYTICS = new RequiresModule() {
        public String value() {
            return RequiresModule.ANALYTICS;
        }

        public com.red_escuela.backend.shared.module.BillingMode minMode() {
            return com.red_escuela.backend.shared.module.BillingMode.MIRROR;
        }

        public Class<? extends Annotation> annotationType() {
            return RequiresModule.class;
        }
    };

    private static ModuleGuard guardWithAnalytics(boolean enabled) {
        FeatureSnapshotHolder holder = Mockito.mock(FeatureSnapshotHolder.class);
        Mockito.when(holder.current()).thenReturn(new FeatureSnapshot(
                Map.of(FeatureCode.ANALYTICS, new FeatureState(enabled, null, 0)), Instant.parse("2026-11-18T12:00:00Z")));
        return new ModuleGuard(new BillingModeGate(holder), new FeatureQuery(holder));
    }

    @Test
    void analyticsDisabledReturnsModuleNotEnabled() {
        assertThatThrownBy(() -> guardWithAnalytics(false).permits(ANALYTICS))
                .isInstanceOf(ModuleNotEnabledException.class);
        assertThat(ErrorCodeResponse.of(new ModuleNotEnabledException("analytics")).code())
                .isEqualTo("MODULE_NOT_ENABLED");
    }

    @Test
    void analyticsEnabledPermitsAccess() {
        assertThat(guardWithAnalytics(true).permits(ANALYTICS)).isTrue();
    }
}
