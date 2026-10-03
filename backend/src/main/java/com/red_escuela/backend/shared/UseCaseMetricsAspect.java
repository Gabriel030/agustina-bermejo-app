package com.red_escuela.backend.shared;

import com.red_escuela.backend.exception.ErrorCodeResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UseCaseMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(UseCaseMetricsAspect.class);
    private static final String METER_NAME = "usecase.duration";
    private static final String USE_CASE_SUFFIX = "UseCase";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_UNEXPECTED = "unexpected";
    private static final String APPLICATION_PACKAGE = "com.red_escuela.backend";

    private final MeterRegistry meterRegistry;
    private final Map<Class<?>, String> useCaseNames = new ConcurrentHashMap<>();

    public UseCaseMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@within(com.red_escuela.backend.shared.UseCase)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        String useCase = useCaseNames.computeIfAbsent(
                AopUtils.getTargetClass(joinPoint.getTarget()), UseCaseMetricsAspect::resolveUseCaseName);
        String method = joinPoint.getSignature().getName();
        String outcome = OUTCOME_SUCCESS;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            outcome = outcomeOf(throwable);
            throw throwable;
        } finally {
            long nanos = sample.stop(Timer.builder(METER_NAME)
                    .description("Duración de cada use case por método y resultado")
                    .tag("usecase", useCase)
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            log.debug("usecase={} method={} outcome={} durationMs={}", useCase, method, outcome, nanos / 1_000_000);
        }
    }

    private static String resolveUseCaseName(Class<?> targetClass) {
        return Arrays.stream(targetClass.getInterfaces())
                .map(Class::getSimpleName)
                .filter(name -> name.endsWith(USE_CASE_SUFFIX))
                .findFirst()
                .orElse(targetClass.getSimpleName());
    }

    private static String outcomeOf(Throwable throwable) {
        if (throwable instanceof IllegalStateException
                || throwable.getClass().getName().startsWith(APPLICATION_PACKAGE)) {
            return ErrorCodeResponse.of(throwable).code();
        }
        return OUTCOME_UNEXPECTED;
    }
}
