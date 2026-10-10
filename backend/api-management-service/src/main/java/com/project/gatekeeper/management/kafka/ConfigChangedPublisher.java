package com.project.gatekeeper.management.kafka;

import com.project.gatekeeper.management.service.ConfigChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ConfigChangedPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangedPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public ConfigChangedPublisher(KafkaTemplate<String, String> kafka,
                                  @Value("${gatekeeper.config-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ConfigChanged event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "CONFIG_CHANGED");
        payload.put("clientId", event.clientId());
        payload.put("reason", event.reason());
        payload.put("at", Instant.now().toString());
        String json = FlatJson.write(payload);
        kafka.send(topic, event.clientId(), json).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Config event was not published. The gateway will pick it up on the next poll. {}", error.getMessage());
            } else {
                log.info("Published config change {} for client {}", event.reason(), event.clientId());
            }
        });
    }
}
