package com.red_escuela.backend.graduation.certificate.engine;

import java.util.List;

public record FittedText(List<String> lines, float fontSize, float lineHeight) {

    public float height() {
        return lines.size() * lineHeight;
    }
}
