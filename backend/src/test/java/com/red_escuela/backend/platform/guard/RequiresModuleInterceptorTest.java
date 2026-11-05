package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.exception.UnknownGuardedModuleException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiresModuleInterceptorTest {

    @RequiresModule(RequiresModule.BILLING)
    public static class ClassGuardedEndpoints {

        public String read() {
            return "read";
        }

        @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
        public String write() {
            return "write";
        }
    }

    public static class UnguardedEndpoints {

        public String read() {
            return "read";
        }
    }

    @RequiresModule("biling")
    public static class UnknownModuleEndpoints {

        public String read() {
            return "read";
        }
    }

    private static RequiresModuleInterceptor interceptorWithBillingIn(BillingMode mode) {
        FeatureSnapshotHolder holder = Mockito.mock(FeatureSnapshotHolder.class);
        Mockito.when(holder.current()).thenReturn(new FeatureSnapshot(
                Map.of(FeatureCode.BILLING, FeatureState.billing(mode, 0)), Instant.parse("2026-10-31T12:00:00Z")));
        return new RequiresModuleInterceptor(new ModuleGuard(new BillingModeGate(holder)));
    }

    private static boolean passes(BillingMode mode, Object controller, String method, String httpMethod) throws Exception {
        HandlerMethod handler = new HandlerMethod(controller, controller.getClass().getMethod(method));
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/anything");
        return interceptorWithBillingIn(mode).preHandle(request, new MockHttpServletResponse(), handler);
    }

    @Test
    void disabledModuleReturns404() {
        assertThatThrownBy(() -> passes(BillingMode.OFF, new ClassGuardedEndpoints(), "read", "GET"))
                .isInstanceOf(NoResourceFoundException.class);
        assertThatThrownBy(() -> passes(BillingMode.OFF, new ClassGuardedEndpoints(), "write", "POST"))
                .isInstanceOf(NoResourceFoundException.class);
    }

    @Test
    void disabledModuleIsResolvedByTheSameResolverThatAnswers404ForARouteThatDoesNotExist() throws Exception {
        HandlerMethod handler = new HandlerMethod(new ClassGuardedEndpoints(), ClassGuardedEndpoints.class.getMethod("read"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Exception thrown = null;
        try {
            interceptorWithBillingIn(BillingMode.OFF).preHandle(request, response, handler);
        } catch (Exception e) {
            thrown = e;
        }
        new DefaultHandlerExceptionResolver().resolveException(request, response, handler, thrown);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void mirrorBlocksFullOnlyEndpoint() throws Exception {
        assertThatThrownBy(() -> passes(BillingMode.MIRROR, new ClassGuardedEndpoints(), "write", "POST"))
                .isInstanceOf(NoResourceFoundException.class);
        assertThat(passes(BillingMode.MIRROR, new ClassGuardedEndpoints(), "read", "GET")).isTrue();
    }

    @Test
    void fullModeLetsEveryGuardedEndpointThrough() throws Exception {
        assertThat(passes(BillingMode.FULL, new ClassGuardedEndpoints(), "read", "GET")).isTrue();
        assertThat(passes(BillingMode.FULL, new ClassGuardedEndpoints(), "write", "POST")).isTrue();
    }

    @Test
    void unguardedEndpointsAreUnaffectedWhenBillingIsOff() throws Exception {
        assertThat(passes(BillingMode.OFF, new UnguardedEndpoints(), "read", "GET")).isTrue();
    }

    @Test
    void anUnknownModuleNameFailsLoudlyInsteadOfOpeningTheEndpoint() {
        assertThatThrownBy(() -> passes(BillingMode.FULL, new UnknownModuleEndpoints(), "read", "GET"))
                .isInstanceOf(UnknownGuardedModuleException.class);
    }
}
