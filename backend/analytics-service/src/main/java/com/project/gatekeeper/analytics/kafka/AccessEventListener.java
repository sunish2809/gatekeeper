package com.project.gatekeeper.analytics.kafka;

import com.project.gatekeeper.analytics.domain.AccessEventDocument;
import com.project.gatekeeper.analytics.repo.AccessEventRepository;
import com.project.gatekeeper.analytics.support.FlatJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class AccessEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccessEventListener.class);

    private final AccessEventRepository events;

    public AccessEventListener(AccessEventRepository events) {
        this.events = events;
    }

    @KafkaListener(topics = "${gatekeeper.access-topic}")
    public void onEvent(String payload) {
        try {
            Map<String, String> values = FlatJson.read(payload);
            if (values.get("correlationId") != null) {
                MDC.put("correlationId", values.get("correlationId"));
            }
            AccessEventDocument document = new AccessEventDocument();
            document.setId(values.get("eventId"));
            document.setCorrelationId(values.get("correlationId"));
            document.setClientId(values.get("clientId"));
            document.setRouteId(values.get("routeId"));
            document.setMethod(values.get("method"));
            document.setPath(values.get("path"));
            document.setStatus(parseInt(values.get("status")));
            document.setLatencyMs(parseLong(values.get("latencyMs")));
            document.setTimestamp(values.get("timestamp") == null ? Instant.now() : Instant.parse(values.get("timestamp")));
            document.setError(values.get("error"));
            events.save(document);
        } catch (DuplicateKeyException ex) {
            log.debug("Duplicate access event ignored");
        } catch (RuntimeException ex) {
            log.warn("Access event was not stored: {}", ex.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    private int parseInt(String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }

    private long parseLong(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }
}
