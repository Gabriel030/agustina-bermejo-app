package com.red_escuela.backend.shared.errortracking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SentryTunnelControllerTest {

    private static final String PATH = "/public/monitoring/tunnel";

    private final SentryTunnel tunnel = mock(SentryTunnel.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SentryTunnelController(tunnel)).build();

    @Test
    void answersNotFoundWhenTheTunnelIsDisabled() throws Exception {
        when(tunnel.isEnabled()).thenReturn(false);

        mockMvc.perform(post(PATH).content("x")).andExpect(status().isNotFound());

        verify(tunnel, never()).forward(any());
    }

    @Test
    void answersPayloadTooLargeFromTheDeclaredContentLengthWithoutReadingTheBody() throws Exception {
        when(tunnel.isEnabled()).thenReturn(true);
        when(tunnel.maxBytes()).thenReturn(4);

        mockMvc.perform(post(PATH).content("12345678")).andExpect(status().isPayloadTooLarge());

        verify(tunnel, never()).forward(any());
    }

    @Test
    void readsAtMostOneByteOverTheLimitAndDelegates() throws Exception {
        when(tunnel.isEnabled()).thenReturn(true);
        when(tunnel.maxBytes()).thenReturn(4);
        when(tunnel.forward(any())).thenReturn(SentryTunnelOutcome.FORWARDED);

        mockMvc.perform(post(PATH).content("1234")).andExpect(status().isNoContent());

        verify(tunnel).forward("1234".getBytes());
    }

    @Test
    void mapsRejectedEnvelopeToBadRequestAndUpstreamFailureToBadGateway() throws Exception {
        when(tunnel.isEnabled()).thenReturn(true);
        when(tunnel.maxBytes()).thenReturn(100);
        when(tunnel.forward(any())).thenReturn(SentryTunnelOutcome.REJECTED_ENVELOPE);
        mockMvc.perform(post(PATH).content("x")).andExpect(status().isBadRequest());

        when(tunnel.forward(any())).thenReturn(SentryTunnelOutcome.UPSTREAM_UNAVAILABLE);
        mockMvc.perform(post(PATH).content("x")).andExpect(status().isBadGateway());

        when(tunnel.forward(any())).thenReturn(SentryTunnelOutcome.UPSTREAM_REJECTED);
        mockMvc.perform(post(PATH).content("x")).andExpect(status().isBadGateway());
    }
}
