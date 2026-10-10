package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.config.CorrelationFilter;
import com.project.gatekeeper.management.service.CatalogService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
public class ConsoleController {

    private static final int MAX_BODY = 16000;
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String gatewayUrl;
    private final String analyticsUrl;
    private final String adminApiKey;
    private final CatalogService catalog;

    public ConsoleController(@Value("${gatekeeper.gateway-url}") String gatewayUrl,
                             @Value("${gatekeeper.analytics-url}") String analyticsUrl,
                             @Value("${gatekeeper.admin-api-key}") String adminApiKey,
                             CatalogService catalog) {
        this.gatewayUrl = stripSlash(gatewayUrl);
        this.analyticsUrl = stripSlash(analyticsUrl);
        this.adminApiKey = adminApiKey;
        this.catalog = catalog;
    }

    @GetMapping("/api/console/status")
    public StatusResponse status() {
        Probe gateway = probe(gatewayUrl + "/health");
        Probe routes = probe(gatewayUrl + "/health/routes");
        Probe analytics = probe(analyticsUrl + "/health");
        Integer routeCount = null;
        if (routes.up() && routes.body() != null && routes.body().contains("\"routes\"")) {
            routeCount = readRoutes(routes.body());
        }
        return new StatusResponse(
                new ServiceStatus("management", true, "UP"),
                new ServiceStatus("gateway", gateway.up(), gateway.up() ? "UP" : gateway.detail()),
                new ServiceStatus("analytics", analytics.up(), analytics.up() ? "UP" : analytics.detail()),
                routeCount);
    }

    @PostMapping("/api/console/try")
    public TryResponse send(@RequestBody TryRequest request) {
        String method = request.method() == null ? "GET" : request.method().trim().toUpperCase(Locale.ROOT);
        String path = request.path() == null ? "" : request.path().trim();
        if (!METHODS.contains(method)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_method", "Use GET, POST, PUT, PATCH, or DELETE");
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://") || path.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_path", "Path must start with / and stay on the gateway");
        }
        long started = System.nanoTime();
        try {
            HttpRequest.Builder builder = correlated(HttpRequest.newBuilder(URI.create(gatewayUrl + path)))
                    .timeout(Duration.ofSeconds(15));
            if (request.apiKey() != null && !request.apiKey().isBlank()) {
                builder.header("X-Api-Key", request.apiKey().trim());
            }
            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                builder.header("Idempotency-Key", request.idempotencyKey().trim());
            }
            if (request.authorization() != null && !request.authorization().isBlank()) {
                String token = request.authorization().trim();
                builder.header("Authorization", token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token);
            }
            if (request.origin() != null && !request.origin().isBlank()) {
                builder.header("Origin", request.origin().trim());
            }
            byte[] body = request.body() == null ? new byte[0] : request.body().getBytes();
            boolean sendBody = !method.equals("GET") && !method.equals("DELETE");
            if (sendBody) {
                builder.header("Content-Type", "application/json");
            }
            builder.method(method, sendBody ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            Map<String, List<String>> headers = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("transfer-encoding")) {
                    headers.put(name, values);
                }
            });
            String responseBody = response.body() == null ? "" : response.body();
            boolean truncated = responseBody.length() > MAX_BODY;
            if (truncated) {
                responseBody = responseBody.substring(0, MAX_BODY);
            }
            return new TryResponse(response.statusCode(), response.headers().firstValue("X-Correlation-Id").orElse(null),
                    latencyMs, headers, responseBody, truncated, null);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            return new TryResponse(0, null, latencyMs, Map.of(), "", false, ex.getMessage());
        }
    }

    @GetMapping("/api/console/overview")
    public ResponseEntity<String> overview(@RequestParam String clientId) {
        catalog.getClient(clientId);
        String url = analyticsUrl + "/api/overview?clientId=" + clientId.trim();
        try {
            HttpRequest request = correlated(HttpRequest.newBuilder(URI.create(url)))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Admin-Key", adminApiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode())
                    .header("Content-Type", "application/json")
                    .body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"analytics_unavailable\",\"message\":\"" + escape(ex.getMessage()) + "\"}");
        }
    }

    @GetMapping("/api/console/events")
    public ResponseEntity<String> events(@RequestParam(required = false) String clientId,
                                         @RequestParam(defaultValue = "20") int limit) {
        String url = analyticsUrl + "/api/events?limit=" + Math.min(Math.max(limit, 1), 50);
        if (clientId != null && !clientId.isBlank()) {
            url = url + "&clientId=" + clientId.trim();
        }
        try {
            HttpRequest request = correlated(HttpRequest.newBuilder(URI.create(url)))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Admin-Key", adminApiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode())
                    .header("Content-Type", "application/json")
                    .body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"analytics_unavailable\",\"message\":\"" + escape(ex.getMessage()) + "\"}");
        }
    }

    private HttpRequest.Builder correlated(HttpRequest.Builder builder) {
        String correlationId = MDC.get("correlationId");
        return correlationId == null ? builder : builder.header(CorrelationFilter.HEADER, correlationId);
    }

    private Probe probe(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new Probe(true, response.body(), null);
            }
            return new Probe(false, response.body(), "HTTP " + response.statusCode());
        } catch (Exception ex) {
            return new Probe(false, null, ex.getMessage());
        }
    }

    private Integer readRoutes(String json) {
        int marker = json.indexOf("\"routes\"");
        if (marker < 0) {
            return null;
        }
        int colon = json.indexOf(':', marker);
        if (colon < 0) {
            return null;
        }
        String tail = json.substring(colon + 1).trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char current = tail.charAt(i);
            if (Character.isDigit(current)) {
                digits.append(current);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.isEmpty() ? null : Integer.parseInt(digits.toString());
    }

    private String stripSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Probe(boolean up, String body, String detail) {
    }

    public record StatusResponse(ServiceStatus management, ServiceStatus gateway, ServiceStatus analytics, Integer routes) {
    }

    public record ServiceStatus(String name, boolean up, String detail) {
    }

    public record TryRequest(String method, String path, String apiKey, String idempotencyKey, String body,
                             String authorization, String origin) {
    }

    public record TryResponse(int status, String correlationId, long latencyMs, Map<String, List<String>> headers,
                              String body, boolean truncated, String error) {
    }
}
