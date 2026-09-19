package com.red_escuela.backend.karate;

import org.springframework.context.ApplicationContext;

public class KarateSpringContext {
    private static ApplicationContext context;

    public static void set(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static ApplicationContext get() {
        return context;
    }
}
