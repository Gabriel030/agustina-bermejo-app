package com.red_escuela.backend.notification.broadcast.template.domain;

public enum TemplateVariable {
    FIRST_NAME("nombre"),
    LAST_NAME("apellido"),
    PROGRAM("carrera"),
    SUBJECT("materia"),
    SECTION("comision"),
    INSTITUTION("institucion"),
    UNSUBSCRIBE_LINK("linkBaja");

    private final String token;

    TemplateVariable(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
