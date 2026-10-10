package com.project.gatekeeper.gateway.controller;

import com.project.gatekeeper.gateway.dto.responseDto.HealthResponse;
import com.project.gatekeeper.gateway.snapshot.SnapshotStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final String serviceName;
    private final String serviceDescription;
    private final SnapshotStore snapshotStore;

    public HealthController(@Value("${info.app.name}") String serviceName,
                            @Value("${info.app.description}") String serviceDescription,
                            SnapshotStore snapshotStore) {
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
        this.snapshotStore = snapshotStore;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(
                new HealthResponse(serviceName, serviceDescription, "UP"));
    }

    @GetMapping("/routes")
    public Map<String, Integer> routes() {
        return Map.of("routes", snapshotStore.size());
    }
}
