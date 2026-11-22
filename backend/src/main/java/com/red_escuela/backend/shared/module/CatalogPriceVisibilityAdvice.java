package com.red_escuela.backend.shared.module;

import java.util.Collection;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(annotations = HidesPricesWhenBillingOff.class)
public class CatalogPriceVisibilityAdvice implements ResponseBodyAdvice<Object> {

    private final CatalogPriceVisibilityPolicy policy;

    public CatalogPriceVisibilityAdvice(CatalogPriceVisibilityPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        HttpMethod method = request.getMethod();
        if (!policy.hidesPrices(method, SecurityContextHolder.getContext().getAuthentication())) {
            return body;
        }
        return withoutPrices(body);
    }

    private static Object withoutPrices(Object body) {
        if (body instanceof CatalogPriced<?> priced) {
            return priced.withoutPrices();
        }
        if (body instanceof Page<?> page) {
            return page.map(CatalogPriceVisibilityAdvice::withoutPrices);
        }
        if (body instanceof Collection<?> items) {
            return items.stream().map(CatalogPriceVisibilityAdvice::withoutPrices).toList();
        }
        return body;
    }
}
