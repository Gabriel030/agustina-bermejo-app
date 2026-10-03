package com.red_escuela.backend.config;

import java.util.concurrent.atomic.AtomicInteger;

public final class TestSubjectCodes {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final int BASE36 = 36;
    private static final int LARGO = 4;
    private static final int MAXIMO = 36 * 36 * 36 * 36;

    private TestSubjectCodes() {
    }

    public static String next() {
        int valor = SECUENCIA.getAndIncrement();
        if (valor >= MAXIMO) {
            throw new IllegalStateException("Se agotaron los códigos de materia de 4 caracteres para esta corrida");
        }
        String base36 = Integer.toString(valor, BASE36).toUpperCase();
        return "0".repeat(LARGO - base36.length()) + base36;
    }
}
