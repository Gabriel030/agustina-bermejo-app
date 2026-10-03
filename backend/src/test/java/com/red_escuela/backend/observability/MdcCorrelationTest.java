package com.red_escuela.backend.observability;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.RequestIdFilter;
import com.red_escuela.backend.config.UserIdMdcFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MdcCorrelationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesARequestIdAndReturnsItInTheResponse() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void reusesTheRequestIdSentByTheClient() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.REQUEST_ID_HEADER, "abc-123"))
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "abc-123"));
    }

    @Test
    void sanitizesAHostileRequestIdBeforeItReachesTheLogs() throws Exception {
        String hostil = "abc\n2026-01-01 ERROR linea-falsa";
        String devuelto = mockMvc.perform(get("/actuator/health").header(RequestIdFilter.REQUEST_ID_HEADER, hostil))
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertThat(devuelto).isNotNull().doesNotContain("\n").doesNotContain(" ");
    }

    @Test
    void leavesNoMdcBehindSoTheNextRequestOnTheSameThreadIsNotMisattributed() throws Exception {
        MDC.put(UserIdMdcFilter.USER_ID_KEY, "999");
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, "viejo");

        mockMvc.perform(get("/actuator/health"));

        assertThat(MDC.get(UserIdMdcFilter.USER_ID_KEY)).isNull();
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNull();
    }
}
