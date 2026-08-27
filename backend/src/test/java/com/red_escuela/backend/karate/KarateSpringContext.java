package com.red_escuela.backend.karate;

import org.springframework.context.ApplicationContext;

/**
 * Holder estático simple: Karate no tiene inyección de dependencias de Spring, así que el
 * runner deja acá el ApplicationContext de la corrida (@BeforeAll) para que
 * {@link KarateFixtures} pueda resolver beans desde código Java llamado por un .feature.
 */
public class KarateSpringContext {

    private static ApplicationContext context;

    public static void set(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static ApplicationContext get() {
        return context;
    }
}
