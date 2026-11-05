package com.red_escuela.backend.security;

import org.springframework.web.method.HandlerMethod;

record MappedEndpoint(String method, String path, HandlerMethod handler) {

    String key() {
        return method + " " + path;
    }
}
