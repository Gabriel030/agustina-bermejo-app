package com.red_escuela.backend.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class SharedInfrastructureWarmUp implements LauncherSessionListener {

    private static final String INTEGRATION_BASE = "com.red_escuela.backend.config.AbstractIntegrationTest";

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (System.getenv("CI") == null || !Boolean.getBoolean("test.fork")) {
            return;
        }
        try {
            Class.forName(INTEGRATION_BASE, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
