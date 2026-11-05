package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Component
public class RequiresModuleInterceptor implements HandlerInterceptor {

    private final ModuleGuard moduleGuard;

    public RequiresModuleInterceptor(ModuleGuard moduleGuard) {
        this.moduleGuard = moduleGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            RequiresModule requirement = requirementOf(handlerMethod);
            if (requirement != null && !moduleGuard.permits(requirement)) {
                throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), request.getRequestURI());
            }
        }
        return true;
    }

    private static RequiresModule requirementOf(HandlerMethod handlerMethod) {
        RequiresModule onMethod = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequiresModule.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequiresModule.class);
    }
}
