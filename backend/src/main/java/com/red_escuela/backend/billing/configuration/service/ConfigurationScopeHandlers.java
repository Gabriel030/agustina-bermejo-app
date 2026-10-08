package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConfigurationScopeHandlers {
    private final Map<ConfigurationScope, ConfigurationScopeHandler> handlersByScope = new EnumMap<>(ConfigurationScope.class);

    public ConfigurationScopeHandlers(List<ConfigurationScopeHandler> handlers) {
        for (ConfigurationScopeHandler handler : handlers) {
            if (handlersByScope.putIfAbsent(handler.scope(), handler) != null) {
                throw new IllegalStateException("Two handlers declared for " + handler.scope());
            }
        }
        for (ConfigurationScope scope : ConfigurationScope.values()) {
            if (!handlersByScope.containsKey(scope)) {
                throw new IllegalStateException("No handler declared for " + scope);
            }
        }
    }

    public ConfigurationScopeHandler handlerOf(ConfigurationScope scope) {
        return handlersByScope.get(scope);
    }
}
