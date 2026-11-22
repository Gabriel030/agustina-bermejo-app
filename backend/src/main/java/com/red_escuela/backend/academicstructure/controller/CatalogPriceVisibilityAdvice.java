package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.usecase.GetCatalogPriceVisibilityUseCase;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(assignableTypes = {ProgramController.class, CourseController.class})
public class CatalogPriceVisibilityAdvice implements ResponseBodyAdvice<Object> {

    private final GetCatalogPriceVisibilityUseCase catalogPriceVisibility;

    public CatalogPriceVisibilityAdvice(GetCatalogPriceVisibilityUseCase catalogPriceVisibility) {
        this.catalogPriceVisibility = catalogPriceVisibility;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!isPricedCatalogBody(body) || catalogPriceVisibility.execute()) {
            return body;
        }
        if (body instanceof List<?> items) {
            return items.stream().map(CatalogPriceVisibilityAdvice::withoutPrices).toList();
        }
        return withoutPrices(body);
    }

    private static boolean isPricedCatalogBody(Object body) {
        if (body instanceof List<?> items) {
            return !items.isEmpty() && isPricedItem(items.get(0));
        }
        return isPricedItem(body);
    }

    private static boolean isPricedItem(Object item) {
        return item instanceof ProgramResponse || item instanceof CourseResponse;
    }

    private static Object withoutPrices(Object item) {
        if (item instanceof ProgramResponse program) {
            return program.withoutPrices();
        }
        if (item instanceof CourseResponse course) {
            return course.withoutPrices();
        }
        return item;
    }
}
