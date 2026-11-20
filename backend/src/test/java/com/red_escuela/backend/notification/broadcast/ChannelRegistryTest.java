package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.exception.ChannelNotAvailableException;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.MessageChannel;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelRegistryTest {

    @Test
    void unregisteredChannelThrowsChannelNotAvailable() {
        MessageChannel email = mock(MessageChannel.class);
        when(email.type()).thenReturn(ChannelType.EMAIL);
        ChannelRegistry registry = new ChannelRegistry(List.of(email));

        assertThatThrownBy(() -> registry.channel(ChannelType.WHATSAPP_API)).isInstanceOf(ChannelNotAvailableException.class);
        assertThat(ErrorCodeResponse.codeOf(ChannelNotAvailableException.class)).isEqualTo("CHANNEL_NOT_AVAILABLE");
    }

    @Test
    void returnsTheRegisteredChannel() {
        MessageChannel email = mock(MessageChannel.class);
        when(email.type()).thenReturn(ChannelType.EMAIL);

        assertThat(new ChannelRegistry(List.of(email)).channel(ChannelType.EMAIL)).isSameAs(email);
    }

    @Test
    void worksWithoutAnyChannel() {
        assertThatThrownBy(() -> new ChannelRegistry(List.of()).channel(ChannelType.EMAIL))
                .isInstanceOf(ChannelNotAvailableException.class);
    }
}
