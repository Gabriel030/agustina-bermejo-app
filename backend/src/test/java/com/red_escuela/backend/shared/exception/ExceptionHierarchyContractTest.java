package com.red_escuela.backend.shared.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(OutputCaptureExtension.class)
class ExceptionHierarchyContractTest {

    private static final AtomicReference<RuntimeException> TO_THROW = new AtomicReference<>();
    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();

    private record Contract(Class<? extends RuntimeException> base, int status, boolean hasBody) {
    }

    private static final List<Contract> CONTRACTS = List.of(
            new Contract(BadRequestException.class, 400, true),
            new Contract(UnauthorizedException.class, 401, false),
            new Contract(ForbiddenException.class, 403, false),
            new Contract(NotFoundException.class, 404, false),
            new Contract(ConflictException.class, 409, true),
            new Contract(TooManyRequestsException.class, 429, false),
            new Contract(ExternalServiceException.class, 502, false));

    @RestController
    static class ThrowingController {
        @GetMapping("/throw")
        void throwConfigured() {
            throw TO_THROW.get();
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void everyConcreteExceptionOfABaseRespondsWithItsBaseStatusAndBody() throws Exception {
        int checked = 0;
        for (Contract contract : CONTRACTS) {
            for (Class<?> type : concreteSubclassesOf(contract.base())) {
                TO_THROW.set((RuntimeException) OBJENESIS.newInstance(type));
                var result = mockMvc.perform(get("/throw")).andReturn().getResponse();

                assertThat(result.getStatus()).as(type.getSimpleName()).isEqualTo(contract.status());
                if (contract.hasBody()) {
                    String expectedCode = ErrorCodeResponse.of(TO_THROW.get()).code();
                    assertThat(result.getContentAsString()).as(type.getSimpleName())
                            .isEqualTo("{\"code\":\"" + expectedCode + "\"}");
                } else if (ReportsErrorCode.class.isAssignableFrom(type)) {
                    assertThat(result.getContentAsString()).as(type.getSimpleName())
                            .isEqualTo("{\"code\":\"" + ErrorCodeResponse.of(TO_THROW.get()).code() + "\"}");
                } else {
                    assertThat(result.getContentAsString()).as(type.getSimpleName()).isEmpty();
                }
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(150);
    }

    @Test
    void aNewConflictIsLoggedWithItsCodeAtWarnLevel(CapturedOutput output) throws Exception {
        class StockAlreadyReservedException extends ConflictException {
            StockAlreadyReservedException() {
                super("sin stock");
            }
        }
        TO_THROW.set(new StockAlreadyReservedException());

        var response = mockMvc.perform(get("/throw")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(output).contains("WARN").contains("STOCK_ALREADY_RESERVED");
    }

    @Test
    void aNewNotFoundRespondsWith404WithoutRegisteringItInTheHandler() throws Exception {
        class WidgetNotFoundException extends NotFoundException {
            WidgetNotFoundException() {
                super("no existe");
            }
        }
        TO_THROW.set(new WidgetNotFoundException());

        var response = mockMvc.perform(get("/throw")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void anExternalServiceFailureIsLoggedAtErrorLevel(CapturedOutput output) throws Exception {
        class GatewayDownException extends ExternalServiceException {
            GatewayDownException() {
                super("caído");
            }
        }
        TO_THROW.set(new GatewayDownException());

        var response = mockMvc.perform(get("/throw")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(output).contains("ERROR").contains("GATEWAY_DOWN");
    }

    private static List<Class<?>> concreteSubclassesOf(Class<?> base) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(base));
        List<Class<?>> types = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.red_escuela.backend")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (!java.lang.reflect.Modifier.isAbstract(type.getModifiers()) && !type.isAnonymousClass()
                    && !type.isLocalClass()) {
                types.add(type);
            }
        }
        return types;
    }
}
