package com.red_escuela.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PerformanceConfigurationIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Environment environment;

    @Test
    void entityManagerIsNotKeptOpenDuringViewRendering() {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    @Test
    void lazyAssociationsAreLoadedInBatches() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        assertThat(sessionFactory.getSessionFactoryOptions().getDefaultBatchFetchSize()).isEqualTo(50);
    }

    @Test
    void jdbcWritesAreBatchedAndOrdered() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        assertThat(sessionFactory.getSessionFactoryOptions().getJdbcBatchSize()).isEqualTo(50);
        assertThat(sessionFactory.getSessionFactoryOptions().isOrderInsertsEnabled()).isTrue();
        assertThat(sessionFactory.getSessionFactoryOptions().isOrderUpdatesEnabled()).isTrue();
    }

    @Test
    void jsonResponsesAreCompressed() {
        assertThat(environment.getProperty("server.compression.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.compression.mime-types")).contains("application/json");
        assertThat(environment.getProperty("server.compression.min-response-size")).isEqualTo("1024");
    }
}
