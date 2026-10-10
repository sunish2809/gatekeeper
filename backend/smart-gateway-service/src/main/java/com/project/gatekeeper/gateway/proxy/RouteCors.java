package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class RouteCors {

    static final String EXPOSE = "X-Correlation-Id, X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After, X-Idempotent-Replay";
    static final String DEFAULT_HEADERS = "Authorization, Content-Type, X-Api-Key, Idempotency-Key, X-Correlation-Id";

    private RouteCors() {
    }

    public static boolean configured(RouteRule route) {
        return route.allowedOrigins() != null && !route.allowedOrigins().isEmpty();
    }

    public static boolean allows(RouteRule route, String origin) {
        if (origin == null || origin.isBlank() || !configured(route)) {
            return false;
        }
        if (route.allowedOrigins().contains("*")) {
            return true;
        }
        return route.allowedOrigins().contains(origin.trim().toLowerCase(Locale.ROOT));
    }

    static boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;
    }

    static void writePreflight(HttpServletResponse response, RouteRule route, HttpServletRequest request, String origin) {
        applyCommon(response, route, origin);
        Set<String> methods = route.methods() == null ? Set.of() : route.methods();
        String allowMethods = methods.stream().sorted().collect(Collectors.joining(", "));
        if (!allowMethods.contains("OPTIONS")) {
            allowMethods = allowMethods.isEmpty() ? "OPTIONS" : allowMethods + ", OPTIONS";
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowMethods);
        String requested = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                requested == null || requested.isBlank() ? DEFAULT_HEADERS : requested);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setContentLength(0);
    }

    static void writeActual(HttpServletResponse response, RouteRule route, String origin) {
        applyCommon(response, route, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, EXPOSE);
    }

    private static void applyCommon(HttpServletResponse response, RouteRule route, String origin) {
        boolean wildcard = route.allowedOrigins() != null && route.allowedOrigins().contains("*");
        String allowed = wildcard ? "*" : origin;
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowed);
        response.setHeader(HttpHeaders.VARY, "Origin");
        if (!wildcard) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }
}
