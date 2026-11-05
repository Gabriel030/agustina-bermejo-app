package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@TestConfiguration
public class RecordingProbeListenerConfiguration {

    public record ProbeEvent(String correlationId, Instant occurredAt, String payload) implements DomainEvent {
    }

    public static class RecordingListener {
        private final Queue<Map<String, String>> mdcSnapshots = new ConcurrentLinkedQueue<>();

        @ApplicationModuleListener
        public void onProbe(ProbeEvent event) {
            mdcSnapshots.add(MDC.getCopyOfContextMap());
        }

        public Queue<Map<String, String>> snapshots() {
            return mdcSnapshots;
        }
    }

    @Bean
    RecordingListener recordingListener() {
        return new RecordingListener();
    }
}
