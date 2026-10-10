package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.config.CorrelationFilter;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import com.project.gatekeeper.gateway.snapshot.TargetUrls;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BackendForwarder {

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade",
            "x-api-key", "x-admin-key", "x-gatekeeper-user");

    private final ConcurrentMap<Integer, RestClient> clients = new ConcurrentHashMap<>();

    public UpstreamResponse forward(RouteRule route, HttpServletRequest request, byte[] body, String correlationId,
                                    String userSubject) {
        URI target = TargetUrls.build(
                route.publicPath(), route.targetUrl(), route.stripPrefix(),
                request.getRequestURI(), request.getQueryString());
        RestClient client = clients.computeIfAbsent(route.timeoutMs(), this::clientFor);
        try {
            RestClient.RequestBodySpec spec = client
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(target)
                    .headers(headers -> copyRequestHeaders(headers, request, route, correlationId, userSubject));
            if (sendsBody(request.getMethod())) {
                spec.body(body);
            }
            return spec.exchange((httpRequest, response) -> new UpstreamResponse(
                    response.getStatusCode().value(),
                    response.getHeaders(),
                    response.getBody().readAllBytes()));
        } catch (ResourceAccessException ex) {
            throw new UpstreamException(isTimeout(ex), ex);
        }
    }

    private RestClient clientFor(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().requestFactory(factory).build();
    }

    private void copyRequestHeaders(HttpHeaders headers, HttpServletRequest request, RouteRule route,
                                    String correlationId, String userSubject) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (SKIP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        headers.set(CorrelationFilter.HEADER, correlationId);
        if (userSubject != null && !userSubject.isBlank()) {
            headers.set("X-Gatekeeper-User", userSubject);
        }
        route.requestHeaders().forEach(headers::set);
    }

    private boolean sendsBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record UpstreamResponse(int status, HttpHeaders headers, byte[] body) {
    }

    public static class UpstreamException extends RuntimeException {
        private final boolean timeout;

        public UpstreamException(boolean timeout, Throwable cause) {
            super(cause);
            this.timeout = timeout;
        }

        public boolean isTimeout() {
            return timeout;
        }
    }

    public static final List<String> SKIP_RESPONSE_HEADERS = List.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length");

    public static boolean skipResponseHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SKIP_RESPONSE_HEADERS.contains(lower) || lower.startsWith("access-control-");
    }

    public static byte[] readBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().readAllBytes();
    }
}
