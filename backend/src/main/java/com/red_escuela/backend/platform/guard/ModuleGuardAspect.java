package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.shared.module.RequiresModule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ModuleGuardAspect {

    private static final Logger log = LoggerFactory.getLogger(ModuleGuardAspect.class);

    private final ModuleGuard moduleGuard;

    public ModuleGuardAspect(ModuleGuard moduleGuard) {
        this.moduleGuard = moduleGuard;
    }

    @Around("(@annotation(com.red_escuela.backend.shared.module.RequiresModule) || @within(com.red_escuela.backend.shared.module.RequiresModule))"
            + " && !@within(org.springframework.web.bind.annotation.RestController)")
    public Object skipWhenModuleIsUnavailable(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) joinPoint.getSignature()).getMethod(), targetClass);
        RequiresModule requirement = AnnotationUtils.findAnnotation(method, RequiresModule.class);
        if (requirement == null) {
            requirement = AnnotationUtils.findAnnotation(targetClass, RequiresModule.class);
        }
        if (requirement != null && !moduleGuard.permits(requirement)) {
            log.info("module_skipped module={} component={}.{}", requirement.value(), targetClass.getSimpleName(), method.getName());
            return null;
        }
        return joinPoint.proceed();
    }
}
