package com.red_escuela.backend.security;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class MatrixRequestFactory {

    private static final long MISSING_ID = 999_999L;
    private static final UUID MISSING_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final MinimalRequestBodies minimalBodies;

    MatrixRequestFactory(MinimalRequestBodies minimalBodies) {
        this.minimalBodies = minimalBodies;
    }

    MockHttpServletRequestBuilder build(MappedEndpoint endpoint, String bodyOverride, String bearerToken) {
        return build(endpoint, bodyOverride, bearerToken, MISSING_ID);
    }

    MockHttpServletRequestBuilder build(MappedEndpoint endpoint, String bodyOverride, String bearerToken, Long resourceId) {
        String uri = expandPathVariables(endpoint, resourceId);
        MockHttpServletRequestBuilder request = isMultipart(endpoint)
                ? multipartRequest(endpoint, uri)
                : MockMvcRequestBuilders.request(HttpMethod.valueOf(endpoint.method()), uri);
        addRequiredQueryParametersAndHeaders(endpoint, request);
        if (hasJsonBody(endpoint)) {
            String body = bodyOverride.isEmpty()
                    ? minimalBodies.jsonBodyFor(endpoint.method(), endpoint.path()).orElse("{}")
                    : bodyOverride;
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return request;
    }

    private MockHttpServletRequestBuilder multipartRequest(MappedEndpoint endpoint, String uri) {
        var multipart = MockMvcRequestBuilders.multipart(HttpMethod.valueOf(endpoint.method()), uri);
        Set<String> fileParts = new LinkedHashSet<>();
        minimalBodies.multipartFilePartFor(endpoint.method(), endpoint.path()).ifPresent(fileParts::add);
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            if (isMultipartPart(parameter)) {
                RequestPart annotation = parameter.getParameterAnnotation(RequestPart.class);
                String name = annotation == null || annotation.name().isEmpty() ? nameOf(parameter) : annotation.name();
                fileParts.add(name);
            }
        }
        fileParts.forEach(part -> multipart.file(new MockMultipartFile(part, "matrix.pdf", "application/pdf", new byte[] {1})));
        minimalBodies.multipartTextFieldsFor(endpoint.method(), endpoint.path())
                .forEach(multipart::param);
        return multipart;
    }

    private boolean isMultipart(MappedEndpoint endpoint) {
        if (minimalBodies.isMultipart(endpoint.method(), endpoint.path())) {
            return true;
        }
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            if (isMultipartPart(parameter)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJsonBody(MappedEndpoint endpoint) {
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    private String expandPathVariables(MappedEndpoint endpoint, Long resourceId) {
        String uri = endpoint.path();
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
            if (annotation != null) {
                String name = annotation.value().isEmpty() ? annotation.name() : annotation.value();
                if (name.isEmpty()) {
                    name = nameOf(parameter);
                }
                Class<?> type = parameter.getParameterType();
                boolean isIdentifier = type == Long.class || type == long.class;
                uri = uri.replace("{" + name + "}", isIdentifier ? String.valueOf(resourceId) : valueFor(type));
            }
        }
        return uri;
    }

    private void addRequiredQueryParametersAndHeaders(MappedEndpoint endpoint, MockHttpServletRequestBuilder request) {
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            RequestParam queryParameter = parameter.getParameterAnnotation(RequestParam.class);
            if (queryParameter != null && queryParameter.required()
                    && queryParameter.defaultValue().equals(ValueConstants.DEFAULT_NONE)
                    && !isMultipartPart(parameter)) {
                String name = queryParameter.name().isEmpty() ? queryParameter.value() : queryParameter.name();
                request.param(name.isEmpty() ? nameOf(parameter) : name, valueFor(elementType(parameter)));
            }
            RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
            if (header != null && header.required()
                    && header.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
                String name = header.name().isEmpty() ? header.value() : header.name();
                request.header(name.isEmpty() ? nameOf(parameter) : name, valueFor(parameter.getParameterType()));
            }
        }
    }

    private String nameOf(MethodParameter parameter) {
        parameter.initParameterNameDiscovery(NAME_DISCOVERER);
        return parameter.getParameterName();
    }

    private boolean isMultipartPart(MethodParameter parameter) {
        return MultipartFile.class.isAssignableFrom(parameter.getParameterType());
    }

    private Class<?> elementType(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (Collection.class.isAssignableFrom(type)) {
            return parameter.nested().getNestedParameterType();
        }
        return type;
    }

    private String valueFor(Class<?> type) {
        if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
            return String.valueOf(MISSING_ID);
        }
        if (type == UUID.class) {
            return MISSING_UUID.toString();
        }
        if (type == LocalDate.class) {
            return "2026-01-15";
        }
        if (type == LocalTime.class) {
            return "09:00";
        }
        if (type == YearMonth.class) {
            return "2026-01";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true";
        }
        if (type.isEnum()) {
            return ((Enum<?>) type.getEnumConstants()[0]).name();
        }
        return "x";
    }
}
