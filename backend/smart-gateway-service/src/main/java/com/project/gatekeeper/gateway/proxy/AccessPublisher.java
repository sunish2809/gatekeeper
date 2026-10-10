package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.support.FlatJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AccessPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccessPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public AccessPublisher(KafkaTemplate<String, String> kafka,
                           @Value("${gatekeeper.access-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publish(String correlationId, String clientId, String routeId, String method, String path,
                        int status, long latencyMs, String error) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("correlationId", correlationId);
        event.put("clientId", clientId);
        event.put("routeId", routeId);
        event.put("method", method);
        event.put("path", path);
        event.put("status", status);
        event.put("latencyMs", latencyMs);
        event.put("timestamp", Instant.now().toString());
        event.put("error", error);
        try {
            kafka.send(topic, correlationId, FlatJson.write(event)).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.warn("Access event was not published: {}", failure.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Access event was not published: {}", ex.getMessage());
        }
    }
}
