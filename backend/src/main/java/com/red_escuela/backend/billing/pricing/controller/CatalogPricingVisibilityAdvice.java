package com.red_escuela.backend.billing.pricing.controller;

import com.red_escuela.backend.billing.dto.CoursePricingResponse;
import com.red_escuela.backend.billing.dto.ProgramPricingResponse;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class CatalogPricingVisibilityAdvice implements ResponseBodyAdvice<Object> {

    private final CurrentBillingModePort billingMode;

    public CatalogPricingVisibilityAdvice(CurrentBillingModePort billingMode) {
        this.billingMode = billingMode;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return ProgramPricingResponse.class.isAssignableFrom(type) || CoursePricingResponse.class.isAssignableFrom(type);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (billingMode.currentMode().isEnabled()) {
            return body;
        }
        if (body instanceof ProgramPricingResponse program) {
            return new ProgramPricingResponse(program.programId(), null, null, null);
        }
        if (body instanceof CoursePricingResponse course) {
            return new CoursePricingResponse(course.courseId(), null);
        }
        return body;
    }
}
