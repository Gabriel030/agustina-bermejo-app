package com.red_escuela.backend.security;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MappedEndpointCatalog {

    private static final String APPLICATION_PACKAGE = "com.red_escuela.backend.";

    private MappedEndpointCatalog() {
    }

    static List<MappedEndpoint> listApplicationEndpoints(RequestMappingHandlerMapping handlerMapping) {
        List<MappedEndpoint> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getName().startsWith(APPLICATION_PACKAGE)) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                throw new IllegalStateException("Mapping sin metodo HTTP explicito: " + info + " en " + handler);
            }
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            for (RequestMethod method : methods) {
                for (String pattern : patterns) {
                    endpoints.add(new MappedEndpoint(method.name(), pattern, handler));
                }
            }
        }
        endpoints.sort(Comparator.comparing(MappedEndpoint::path).thenComparing(MappedEndpoint::method));
        return endpoints;
    }
}
