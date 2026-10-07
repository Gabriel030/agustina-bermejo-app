package com.red_escuela.backend.shared.event;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DomainEventCorrelationAspect {
    private static final Logger log = LoggerFactory.getLogger(DomainEventCorrelationAspect.class);

    @Around("@annotation(org.springframework.modulith.events.ApplicationModuleListener)")
    public Object correlateListenerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length != 1 || !(args[0] instanceof DomainEvent event)) {
            return joinPoint.proceed();
        }
        String previous = MDC.get(CorrelationIds.CORRELATION_ID_KEY);
        MDC.put(CorrelationIds.CORRELATION_ID_KEY, event.correlationId());
        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            log.warn("Event listener failed and its publication stays incomplete: correlationId={}, eventType={}, listenerId={}.{}",
                    event.correlationId(), event.getClass().getName(), signature.getDeclaringTypeName(), signature.getName(), failure);
            throw failure;
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIds.CORRELATION_ID_KEY);
            } else {
                MDC.put(CorrelationIds.CORRELATION_ID_KEY, previous);
            }
        }
    }
}
