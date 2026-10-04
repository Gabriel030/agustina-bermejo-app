package com.red_escuela.backend.shared.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public final class QueryCountAssertions {

    private QueryCountAssertions() {
    }

    public static <T> T assertAtMostQueries(EntityManagerFactory entityManagerFactory, int maxQueries, Supplier<T> action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            T result = action.get();
            assertThat(statistics.getPrepareStatementCount())
                    .as("JDBC statements executed")
                    .isLessThanOrEqualTo((long) maxQueries);
            return result;
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}
