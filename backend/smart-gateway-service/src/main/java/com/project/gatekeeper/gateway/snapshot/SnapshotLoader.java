package com.project.gatekeeper.gateway.snapshot;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.ClientSnapshot;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteSnapshot;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.SnapshotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SnapshotLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotLoader.class);

    private final RestClient restClient;
    private final SnapshotStore store;
    private final String managementUrl;
    private final String adminApiKey;

    public SnapshotLoader(RestClient.Builder builder, SnapshotStore store,
                          @Value("${gatekeeper.management-url}") String managementUrl,
                          @Value("${gatekeeper.admin-api-key}") String adminApiKey) {
        this.restClient = builder.build();
        this.store = store;
        this.managementUrl = managementUrl;
        this.adminApiKey = adminApiKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    public void refresh() {
        try {
            SnapshotResponse snapshot = restClient.get()
                    .uri(managementUrl + "/internal/snapshot")
                    .header("X-Admin-Key", adminApiKey)
                    .retrieve()
                    .body(SnapshotResponse.class);
            List<RouteRule> rules = flatten(snapshot);
            store.replace(rules);
            log.info("Loaded {} route(s) into memory", rules.size());
        } catch (RuntimeException ex) {
            log.warn("Route snapshot was not refreshed. Keeping the previous in-memory routes. {}", ex.getMessage());
        }
    }

    private List<RouteRule> flatten(SnapshotResponse snapshot) {
        List<RouteRule> rules = new ArrayList<>();
        if (snapshot == null || snapshot.clients() == null) {
            return rules;
        }
        for (ClientSnapshot client : snapshot.clients()) {
            if (client.routes() == null) {
                continue;
            }
            for (RouteSnapshot route : client.routes()) {
                if (!route.enabled()) {
                    continue;
                }
                rules.add(new RouteRule(
                        route.id(),
                        client.id(),
                        client.apiKey(),
                        setOf(client.roles()),
                        route.publicPath(),
                        route.targetUrl(),
                        methods(route.methods()),
                        route.timeoutMs(),
                        route.authRequired(),
                        setOf(route.allowedRoles()),
                        route.rateLimit(),
                        route.rateWindowSeconds(),
                        route.idempotentPost(),
                        route.stripPrefix(),
                        route.requestHeaders() == null ? Map.of() : Map.copyOf(route.requestHeaders()),
                        route.requireUserJwt()));
            }
        }
        return rules;
    }

    private Set<String> setOf(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(values);
    }

    private Set<String> methods(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> methods = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                methods.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Set.copyOf(methods);
    }
}
