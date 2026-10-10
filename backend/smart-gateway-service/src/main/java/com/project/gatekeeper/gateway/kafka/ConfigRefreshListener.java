package com.project.gatekeeper.gateway.kafka;

import com.project.gatekeeper.gateway.snapshot.SnapshotLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    private final SnapshotLoader loader;

    public ConfigRefreshListener(SnapshotLoader loader) {
        this.loader = loader;
    }

    @KafkaListener(topics = "${gatekeeper.config-topic}")
    public void onConfigChanged(String payload) {
        log.info("Config change received: {}", payload);
        loader.refresh();
    }
}
