package com.red_escuela.backend.config;

import com.red_escuela.backend.config.infrastructure.DemoApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoEducationalSeedRunnerTest {

    private static final String ADMIN = "administrativo.demo";

    private final DemoEducationalSeedRunner runner = new DemoEducationalSeedRunner(mock(Environment.class), mock(JdbcTemplate.class));
    private final DemoApiClient api = mock(DemoApiClient.class);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void reusesTheActiveAnnualTermOfTheYearInsteadOfCreatingAnother() {
        when(api.get(ADMIN, "/terms")).thenReturn(mapper.readTree(
                "[{\"id\":3,\"year\":2025,\"type\":\"ANNUAL\",\"status\":\"ACTIVE\"},{\"id\":9,\"year\":2026,\"type\":\"ANNUAL\",\"status\":\"ACTIVE\"}]"));

        long termId = runner.createOpenTerm(api);

        assertThat(termId).isEqualTo(9L);
        verify(api, never()).post(eq(ADMIN), eq("/terms"), any());
    }

    @Test
    void createsAndOpensTheTermWhenNoActiveAnnualTermExistsForTheYear() {
        when(api.get(ADMIN, "/terms")).thenReturn(mapper.readTree(
                "[{\"id\":4,\"year\":2026,\"type\":\"ANNUAL\",\"status\":\"INACTIVE\"},{\"id\":5,\"year\":2026,\"type\":\"SEMESTER\",\"status\":\"ACTIVE\"}]"));
        when(api.post(eq(ADMIN), eq("/terms"), any())).thenReturn(mapper.readTree("{\"id\":11}"));

        long termId = runner.createOpenTerm(api);

        assertThat(termId).isEqualTo(11L);
        verify(api).post(ADMIN, "/terms/11/enrollment/open", null);
    }
}
