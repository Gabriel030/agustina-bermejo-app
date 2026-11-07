package com.red_escuela.backend.demo;

import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Profile("demo")
@Order(DemoSeedRunner.RUN_ORDER)
public class DemoSeedRunner implements ApplicationRunner {
    public static final int RUN_ORDER = 100;

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final Environment environment;
    private final DemoSeedSession session;
    private final DemoClock demoClock;
    private final List<DemoSeedStep> steps;
    private final String datasetName;
    private final boolean enabledOnStartup;

    public DemoSeedRunner(Environment environment, DemoSeedSession session, DemoClock demoClock,
                          List<DemoSeedStep> steps, @Value("${demo.dataset:full}") String datasetName,
                          @Value("${demo.seed.enabled:true}") boolean enabledOnStartup) {
        this.environment = environment;
        this.session = session;
        this.demoClock = demoClock;
        this.steps = steps.stream().sorted(Comparator.comparingInt(DemoSeedStep::order)).toList();
        this.datasetName = datasetName;
        this.enabledOnStartup = enabledOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabledOnStartup) {
            return;
        }
        seedThroughPort(environment.getRequiredProperty("local.server.port", Integer.class));
    }

    public void seedThroughPort(int port) {
        DemoDataset dataset = DemoDataset.of(datasetName);
        ApplicationTime.useClock(demoClock);
        session.open(port);
        if (alreadySeeded()) {
            log.info("Seed demo: ya hay datos ({}), no se siembra de nuevo", DemoCatalog.EARLY_CHILDHOOD);
            return;
        }
        long startedAt = System.currentTimeMillis();
        for (DemoSeedStep step : steps) {
            runStep(step, dataset);
        }
        log.info("Seed demo listo: dataset={}, pasos={}, {} ms", dataset.name(), steps.size(), System.currentTimeMillis() - startedAt);
    }

    private void runStep(DemoSeedStep step, DemoDataset dataset) {
        long startedAt = System.currentTimeMillis();
        try {
            step.seed(dataset);
        } catch (RuntimeException failure) {
            log.warn("Seed demo: el paso {} falló: {}", step.label(), failure.getMessage());
            throw new IllegalStateException("Seed demo incompleto, falló el paso " + step.label(), failure);
        }
        log.info("Seed demo: {} ok en {} ms", step.label(), System.currentTimeMillis() - startedAt);
    }

    private boolean alreadySeeded() {
        return session.findIdByName(session.api().get(DemoPersona.ADMIN, "/programs"), DemoCatalog.EARLY_CHILDHOOD) != null;
    }
}
