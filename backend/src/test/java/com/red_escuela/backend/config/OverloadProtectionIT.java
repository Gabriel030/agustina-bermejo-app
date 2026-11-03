package com.red_escuela.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OverloadProtectionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;
    @Autowired
    private ServerProperties serverProperties;
    @Autowired
    private MultipartProperties multipartProperties;
    @Autowired
    private HikariDataSource dataSource;
    @Autowired
    private SpringDataWebProperties springDataWebProperties;
    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    void passwordResetIsRateLimitedThroughTheRealSecurityChainWithRetryAfter() throws Exception {
        String body = "{\"resetToken\":\"x\",\"newPassword\":\"Abcdef12\"}";
        for (int i = 0; i < 200; i++) {
            mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body)
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.77");
                        return request;
                    }));
        }

        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.77");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void requestSizeAndPageSizeLimitsAreConfigured() {
        assertThat(multipartProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(5));
        assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(6));
        assertThat(serverProperties.getTomcat().getMaxSwallowSize()).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(serverProperties.getTomcat().getMaxHttpFormPostSize()).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(springDataWebProperties.getPageable().getMaxPageSize()).isEqualTo(200);
        assertThat(handlerAdapter.getArgumentResolvers()).anyMatch(PageableHandlerMethodArgumentResolver.class::isInstance);
    }

    @Test
    void forwardedHeadersAreNotTrustedByDefaultAndPoolAndQueryTimeoutsAreBounded() {
        assertThat(serverProperties.getForwardHeadersStrategy()).isEqualTo(ServerProperties.ForwardHeadersStrategy.NONE);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(10000);
        assertThat(dataSource.getValidationTimeout()).isEqualTo(5000);
        assertThat(environment.getProperty("spring.jpa.properties.jakarta.persistence.query.timeout")).isEqualTo("60000");
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout")).isEqualTo("300");
    }
}
