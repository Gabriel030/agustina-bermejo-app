package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.exception.ChannelNotAvailableException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChannelRegistry {

    private final Map<ChannelType, MessageChannel> channelsByType;

    public ChannelRegistry(List<MessageChannel> channels) {
        this.channelsByType = channels.stream().collect(Collectors.toUnmodifiableMap(MessageChannel::type, Function.identity()));
    }

    public MessageChannel channel(ChannelType type) {
        return Optional.ofNullable(channelsByType.get(type)).orElseThrow(() -> new ChannelNotAvailableException(type));
    }
}
