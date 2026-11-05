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

import java.time.Clock;
import java.time.Duration;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduledJobFailureAspect {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobFailureAspect.class);

    private final ScheduledJobFailureCounter failureCounter;

    private final ScheduledJobRunRecorder runRecorder;

    public ScheduledJobFailureAspect(MeterRegistry meterRegistry, Clock clock) {
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
        this.runRecorder = new ScheduledJobRunRecorder(meterRegistry, clock);
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) && @annotation(net.javacrumbs.shedlock.spring.annotation.SchedulerLock)")
    public Object countFailures(ProceedingJoinPoint joinPoint) throws Throwable {
        String job = AopUtils.getTargetClass(joinPoint.getTarget()).getSimpleName();
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            runRecorder.recordSuccess(job, Duration.ofNanos(System.nanoTime() - start));
            return result;
        } catch (Throwable failure) {
            runRecorder.recordFailure(job, Duration.ofNanos(System.nanoTime() - start));
            failureCounter.record(job, failure);
            log.error("Fallo la ejecucion programada job={}", job, failure);
            throw failure;
        }
    }
}
