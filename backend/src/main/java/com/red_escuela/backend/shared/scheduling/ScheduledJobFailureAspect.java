package com.red_escuela.backend.shared.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduledJobFailureAspect {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobFailureAspect.class);

    private final ScheduledJobFailureCounter failureCounter;

    public ScheduledJobFailureAspect(MeterRegistry meterRegistry) {
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) && @annotation(net.javacrumbs.shedlock.spring.annotation.SchedulerLock)")
    public Object countFailures(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            String job = AopUtils.getTargetClass(joinPoint.getTarget()).getSimpleName();
            failureCounter.record(job, failure);
            log.error("Fallo la ejecucion programada job={}", job, failure);
            throw failure;
        }
    }
}
