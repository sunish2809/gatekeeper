package com.project.gatekeeper.gateway.snapshot;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConfigPoller {

    private final SnapshotLoader loader;

    public ConfigPoller(SnapshotLoader loader) {
        this.loader = loader;
    }

    @Scheduled(fixedDelayString = "${gatekeeper.config-refresh-ms:30000}")
    public void poll() {
        loader.refresh();
    }
}
