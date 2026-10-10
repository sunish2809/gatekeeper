package com.project.gatekeeper.management.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final String serviceName;
    private final String serviceDescription;

    public HealthController(@Value("${info.app.name}") String serviceName,
                            @Value("${info.app.description}") String serviceDescription) {
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "serviceName", serviceName,
                "serviceDescription", serviceDescription,
                "appStatus", "UP");
    }
}
