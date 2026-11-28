package com.red_escuela.backend.platform.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.platform.exception.BillingActivationBlockedByCohortsWithoutTemplateException;
import com.red_escuela.backend.shared.domain.TemplateLessCohortPort;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

class BillingActivationCohortGuardTest {

    private final TemplateLessCohortPort port = mock(TemplateLessCohortPort.class);
    private final BillingActivationCohortGuard guard = new BillingActivationCohortGuard(port);

    @Test
    void offToMirrorIsBlockedWhenCohortsWithoutTemplateExist() {
        when(port.countCohortsWithoutBillingTemplate()).thenReturn(2L);

        assertThatThrownBy(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.OFF, BillingMode.MIRROR))
                .isInstanceOf(BillingActivationBlockedByCohortsWithoutTemplateException.class)
                .hasMessageContaining("2 cohorte(s) sin plantilla");
    }

    @Test
    void offToFullIsBlockedWhenCohortsWithoutTemplateExist() {
        when(port.countCohortsWithoutBillingTemplate()).thenReturn(1L);

        assertThatThrownBy(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.OFF, BillingMode.FULL))
                .isInstanceOf(BillingActivationBlockedByCohortsWithoutTemplateException.class);
    }

    @Test
    void offToMirrorAndFullAreAllowedWithoutCohortsWithoutTemplate() {
        when(port.countCohortsWithoutBillingTemplate()).thenReturn(0L);

        assertThatCode(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.OFF, BillingMode.MIRROR)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.OFF, BillingMode.FULL)).doesNotThrowAnyException();
    }

    @Test
    void transitionsNotStartingFromOffAreNotChecked() {
        assertThatCode(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.MIRROR, BillingMode.FULL)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.FULL, BillingMode.OFF)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.MIRROR, BillingMode.OFF)).doesNotThrowAnyException();
        verifyNoInteractions(port);
    }

    @Test
    void exposesTheCohortCount() {
        when(port.countCohortsWithoutBillingTemplate()).thenReturn(5L);

        assertThatThrownBy(() -> guard.requireNoCohortsWithoutTemplate(BillingMode.OFF, BillingMode.FULL))
                .satisfies(error -> assertThat(((BillingActivationBlockedByCohortsWithoutTemplateException) error).cohortCount()).isEqualTo(5L));
    }
}
