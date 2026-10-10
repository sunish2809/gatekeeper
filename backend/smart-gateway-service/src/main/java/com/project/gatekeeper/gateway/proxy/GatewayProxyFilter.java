package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.config.CorrelationFilter;
import com.project.gatekeeper.gateway.proxy.BackendForwarder.UpstreamException;
import com.project.gatekeeper.gateway.proxy.BackendForwarder.UpstreamResponse;
import com.project.gatekeeper.gateway.proxy.IdempotencyService.Result;
import com.project.gatekeeper.gateway.proxy.RateLimitService.Decision;
import com.project.gatekeeper.gateway.proxy.RateLimitService.RateLimiterUnavailableException;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.Caller;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.Match;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import com.project.gatekeeper.gateway.snapshot.SnapshotStore;
import com.project.gatekeeper.gateway.support.FlatJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GatewayProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyFilter.class);
    private static final String API_KEY = "X-Api-Key";

    private final SnapshotStore store;
    private final RateLimitService rateLimits;
    private final IdempotencyService idempotency;
    private final BackendForwarder forwarder;
    private final AccessPublisher publisher;
    private final UserTokenService userTokens;
    private final int maxBodyBytes;

    public GatewayProxyFilter(SnapshotStore store, RateLimitService rateLimits, IdempotencyService idempotency,
                              BackendForwarder forwarder, AccessPublisher publisher, UserTokenService userTokens,
                              @org.springframework.beans.factory.annotation.Value("${gatekeeper.max-body-bytes:1048576}") int maxBodyBytes) {
        this.store = store;
        this.rateLimits = rateLimits;
        this.idempotency = idempotency;
        this.forwarder = forwarder;
        this.publisher = publisher;
        this.userTokens = userTokens;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") || path.startsWith("/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String correlationId = CorrelationFilter.id(request);
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = 500;
        String clientId = null;
        String routeId = null;
        String error = null;
        boolean publish = true;
        try {
            boolean preflight = RouteCors.isPreflight(request);
            String matchMethod = preflight ? request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) : method;
            Match match = store.match(matchMethod, path);
            if (match.methodNotAllowed()) {
                status = HttpServletResponse.SC_METHOD_NOT_ALLOWED;
                error = "method_not_allowed";
                writeError(response, status, error, "Method is not allowed for this route", correlationId);
                return;
            }
            RouteRule route = match.route();
            if (route == null) {
                status = HttpServletResponse.SC_NOT_FOUND;
                error = "not_found";
                writeError(response, status, error, "No route matches " + method + " " + path, correlationId);
                return;
            }
            clientId = route.clientId();
            routeId = route.routeId();

            String origin = request.getHeader(HttpHeaders.ORIGIN);
            boolean corsOk = RouteCors.allows(route, origin);
            if (preflight) {
                publish = false;
                if (!corsOk) {
                    status = HttpServletResponse.SC_FORBIDDEN;
                    error = "cors_origin_denied";
                    writeError(response, status, error, "Origin is not allowed for this route", correlationId);
                    return;
                }
                RouteCors.writePreflight(response, route, request, origin);
                status = HttpServletResponse.SC_NO_CONTENT;
                return;
            }
            if (origin != null && !origin.isBlank() && RouteCors.configured(route) && !corsOk) {
                status = HttpServletResponse.SC_FORBIDDEN;
                error = "cors_origin_denied";
                writeError(response, status, error, "Origin is not allowed for this route", correlationId);
                return;
            }
            if (corsOk) {
                RouteCors.writeActual(response, route, origin);
            }

            if (route.authRequired()) {
                Auth auth = authorize(request, route);
                if (auth != Auth.OK) {
                    status = auth == Auth.UNAUTHORIZED ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN;
                    error = auth == Auth.UNAUTHORIZED ? "unauthorized" : "forbidden";
                    String message = auth == Auth.UNAUTHORIZED
                            ? "Send the client X-Api-Key"
                            : "API key is not allowed for this route";
                    writeError(response, status, error, message, correlationId);
                    return;
                }
            }

            UserTokenService.Outcome token = userTokens.verify(route, request.getHeader(HttpHeaders.AUTHORIZATION));
            if (token.status() == UserTokenService.Status.UNAVAILABLE) {
                status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
                error = "dependency_unavailable";
                writeError(response, status, error, "Identity provider is unavailable", correlationId);
                return;
            }
            if (token.status() == UserTokenService.Status.INVALID) {
                status = HttpServletResponse.SC_UNAUTHORIZED;
                error = "invalid_user_token";
                writeError(response, status, error, "Send a valid user JWT in Authorization: Bearer", correlationId);
                return;
            }
            String userSubject = token.subject();

            String presentedKey = request.getHeader(API_KEY);
            int contentLength = request.getContentLength();
            if (contentLength > maxBodyBytes) {
                status = 413;
                error = "body_too_large";
                writeError(response, status, error, "Request body is larger than " + maxBodyBytes + " bytes", correlationId);
                return;
            }

            Decision decision = rateLimits.check(route, RateLimitService.identity(presentedKey, userSubject, clientIp(request)));
            if (decision.limit() > 0) {
                response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
                response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
            }
            if (!decision.allowed()) {
                status = 429;
                error = "rate_limited";
                response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
                writeError(response, status, error, "Rate limit exceeded", correlationId);
                return;
            }

            String idempotencyKey = request.getHeader("Idempotency-Key");
            boolean trackIdempotency = route.idempotentPost() && "POST".equals(method);
            if (trackIdempotency && (idempotencyKey == null || idempotencyKey.isBlank())) {
                status = HttpServletResponse.SC_BAD_REQUEST;
                error = "idempotency_key_required";
                writeError(response, status, error, "Send Idempotency-Key for this POST route", correlationId);
                return;
            }
            if (trackIdempotency) {
                Result result = idempotency.begin(route.routeId(), idempotencyKey);
                if (result instanceof Result.Replay replay) {
                    status = replay.status();
                    response.setHeader("X-Idempotent-Replay", "true");
                    writeBytes(response, status, replay.contentType(), replay.body());
                    return;
                }
                if (result instanceof Result.Busy) {
                    status = HttpServletResponse.SC_CONFLICT;
                    error = "idempotency_in_progress";
                    writeError(response, status, error, "A request with this Idempotency-Key is still running", correlationId);
                    return;
                }
            }

            byte[] body = BackendForwarder.readBody(request);
            if (body.length > maxBodyBytes) {
                status = 413;
                error = "body_too_large";
                writeError(response, status, error, "Request body is larger than " + maxBodyBytes + " bytes", correlationId);
                return;
            }
            UpstreamResponse upstream = forwarder.forward(route, request, body, correlationId, userSubject);
            status = upstream.status();
            if (trackIdempotency) {
                idempotency.complete(route.routeId(), idempotencyKey, status,
                        upstream.headers().getFirst(HttpHeaders.CONTENT_TYPE), upstream.body());
            }
            writeUpstream(response, upstream);
            log.info("{} {} -> {} {}", method, path, route.targetUrl(), status);
        } catch (RateLimiterUnavailableException ex) {
            status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            error = "dependency_unavailable";
            writeError(response, status, error, "Redis is unavailable", correlationId);
        } catch (UpstreamException ex) {
            status = ex.isTimeout() ? HttpServletResponse.SC_GATEWAY_TIMEOUT : HttpServletResponse.SC_BAD_GATEWAY;
            error = ex.isTimeout() ? "timeout" : "bad_gateway";
            writeError(response, status, error, ex.isTimeout() ? "Backend timed out" : "Backend is unreachable", correlationId);
        } catch (RuntimeException ex) {
            log.warn("Gateway request failed", ex);
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            error = "gateway_error";
            writeError(response, status, error, "Gateway failed to process the request", correlationId);
        } finally {
            if (publish) {
                long latencyMs = (System.nanoTime() - started) / 1_000_000;
                publisher.publish(correlationId, clientId, routeId, method, path, status, latencyMs, error);
            }
        }
    }

    // The owning client's key always passes. Another client's key passes only when it belongs to the
    // same admin and holds one of the route's allowed roles; keys from other admins are treated as unknown.
    private Auth authorize(HttpServletRequest request, RouteRule route) {
        String presented = request.getHeader(API_KEY);
        if (presented == null) {
            return Auth.UNAUTHORIZED;
        }
        if (MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                route.apiKey().getBytes(StandardCharsets.UTF_8))) {
            return Auth.OK;
        }
        Caller caller = store.caller(presented);
        if (caller == null || route.adminId() == null || !route.adminId().equals(caller.adminId())) {
            return Auth.UNAUTHORIZED;
        }
        for (String role : caller.roles()) {
            if (route.allowedRoles().contains(role)) {
                return Auth.OK;
            }
        }
        return Auth.FORBIDDEN;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private enum Auth {
        OK, UNAUTHORIZED, FORBIDDEN
    }

    private void writeUpstream(HttpServletResponse response, UpstreamResponse upstream) throws IOException {
        response.setStatus(upstream.status());
        upstream.headers().forEach((name, values) -> {
            if (!BackendForwarder.skipResponseHeader(name)) {
                for (String value : values) {
                    response.addHeader(name, value);
                }
            }
        });
        writeBody(response, upstream.body());
    }

    private void writeBytes(HttpServletResponse response, int status, String contentType, byte[] body) throws IOException {
        response.setStatus(status);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        writeBody(response, body);
    }

    private void writeError(HttpServletResponse response, int status, String error, String message, String correlationId)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("message", message);
        payload.put("correlationId", correlationId);
        response.setStatus(status);
        response.setContentType("application/json");
        writeBody(response, FlatJson.write(payload).getBytes(StandardCharsets.UTF_8));
    }

    private void writeBody(HttpServletResponse response, byte[] body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body;
        response.setContentLength(payload.length);
        response.getOutputStream().write(payload);
        response.getOutputStream().flush();
    }
}
