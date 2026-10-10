package com.project.gatekeeper.analytics.web;

import com.project.gatekeeper.analytics.domain.AccessEventDocument;
import com.project.gatekeeper.analytics.service.AnalyticsQueryService;
import com.project.gatekeeper.analytics.service.AnalyticsQueryService.Summary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AnalyticsController {

    private final AnalyticsQueryService analytics;
    private final String serviceName;
    private final String serviceDescription;

    public AnalyticsController(AnalyticsQueryService analytics,
                               @Value("${info.app.name}") String serviceName,
                               @Value("${info.app.description}") String serviceDescription) {
        this.analytics = analytics;
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "serviceName", serviceName,
                "serviceDescription", serviceDescription,
                "appStatus", "UP");
    }

    @GetMapping("/api/events")
    public List<AccessEventDocument> events(@RequestParam(required = false) String clientId,
                                            @RequestParam(defaultValue = "50") int limit) {
        return analytics.recent(clientId, limit);
    }

    @GetMapping("/api/summary")
    public Summary summary(@RequestParam(required = false) String clientId) {
        return analytics.summarize(clientId);
    }

    @GetMapping("/api/overview")
    public AnalyticsQueryService.Overview overview(@RequestParam(required = false) String clientId) {
        return analytics.overview(clientId);
    }
}
