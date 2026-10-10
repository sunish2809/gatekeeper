package com.project.gatekeeper.management.service;

import com.project.gatekeeper.management.api.CatalogDtos.AuditResponse;
import com.project.gatekeeper.management.api.CatalogDtos.ClientResponse;
import com.project.gatekeeper.management.api.CatalogDtos.ClientSnapshot;
import com.project.gatekeeper.management.api.CatalogDtos.CreateClientRequest;
import com.project.gatekeeper.management.api.CatalogDtos.RouteResponse;
import com.project.gatekeeper.management.api.CatalogDtos.RouteSnapshot;
import com.project.gatekeeper.management.api.CatalogDtos.SnapshotResponse;
import com.project.gatekeeper.management.api.CatalogDtos.UpsertRouteRequest;
import com.project.gatekeeper.management.domain.AuditEntry;
import com.project.gatekeeper.management.domain.ClientEntity;
import com.project.gatekeeper.management.domain.RouteEntity;
import com.project.gatekeeper.management.repo.AuditRepository;
import com.project.gatekeeper.management.repo.ClientRepository;
import com.project.gatekeeper.management.repo.RouteRepository;
import com.project.gatekeeper.management.security.CurrentAdmin;
import com.project.gatekeeper.management.support.Texts;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private final ClientRepository clients;
    private final RouteRepository routes;
    private final AuditRepository audits;
    private final ApplicationEventPublisher events;
    private final CurrentAdmin currentAdmin;

    public CatalogService(ClientRepository clients, RouteRepository routes, AuditRepository audits,
                          ApplicationEventPublisher events, CurrentAdmin currentAdmin) {
        this.clients = clients;
        this.routes = routes;
        this.audits = audits;
        this.events = events;
        this.currentAdmin = currentAdmin;
    }

    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        String apiKey = request.apiKey() == null || request.apiKey().isBlank()
                ? "gk_" + UUID.randomUUID().toString().replace("-", "")
                : request.apiKey().trim();
        if (clients.existsByApiKey(apiKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "API key already exists");
        }
        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID().toString());
        client.setAdminId(currentAdmin.id());
        client.setName(request.name().trim());
        client.setApiKey(apiKey);
        client.setActive(true);
        client.setRoles(Texts.csv(request.roles()));
        clients.save(client);
        events.publishEvent(new ConfigChanged(client.getId(), "client.created"));
        return toClient(client);
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients() {
        return clients.findByAdminId(currentAdmin.id()).stream().map(this::toClient).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(String id) {
        return toClient(requireClient(id));
    }

    @Transactional
    public void deleteClient(String id) {
        requireClient(id);
        routes.deleteByClientId(id);
        clients.deleteById(id);
        events.publishEvent(new ConfigChanged(id, "client.deleted"));
    }

    @Transactional
    public RouteResponse createRoute(String clientId, UpsertRouteRequest request) {
        requireClient(clientId);
        RouteEntity route = new RouteEntity();
        route.setId(UUID.randomUUID().toString());
        route.setClientId(clientId);
        apply(route, request);
        ensurePathAvailable(route.getPublicPath(), null);
        routes.save(route);
        events.publishEvent(new ConfigChanged(clientId, "route.created"));
        return toRoute(route);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(String clientId) {
        requireClient(clientId);
        return routes.findByClientId(clientId).stream().map(this::toRoute).toList();
    }

    @Transactional
    public RouteResponse updateRoute(String clientId, String routeId, UpsertRouteRequest request) {
        RouteEntity route = requireRoute(clientId, routeId);
        apply(route, request);
        ensurePathAvailable(route.getPublicPath(), route.getId());
        routes.save(route);
        events.publishEvent(new ConfigChanged(clientId, "route.updated"));
        return toRoute(route);
    }

    @Transactional
    public void deleteRoute(String clientId, String routeId) {
        RouteEntity route = requireRoute(clientId, routeId);
        routes.delete(route);
        events.publishEvent(new ConfigChanged(clientId, "route.deleted"));
    }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot() {
        List<ClientSnapshot> body = clients.findAll().stream()
                .filter(ClientEntity::isActive)
                .map(client -> new ClientSnapshot(
                        client.getId(),
                        client.getName(),
                        client.getApiKey(),
                        client.isActive(),
                        Texts.split(client.getRoles()),
                        routes.findByClientId(client.getId()).stream()
                                .filter(RouteEntity::isEnabled)
                                .map(this::toSnapshotRoute)
                                .toList(),
                        client.getAdminId()))
                .toList();
        return new SnapshotResponse(body);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> recentAudits() {
        return audits.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toAudit)
                .toList();
    }

    private void apply(RouteEntity route, UpsertRouteRequest request) {
        int timeoutMs = Texts.or(request.timeoutMs(), 5000);
        int rateWindow = Texts.or(request.rateWindowSeconds(), 60);
        if (timeoutMs < 1 || rateWindow < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_route", "timeoutMs and rateWindowSeconds must be positive");
        }
        route.setName(request.name().trim());
        route.setPublicPath(Texts.publicPath(request.publicPath()));
        route.setTargetUrl(Texts.targetUrl(request.targetUrl()));
        route.setMethods(Texts.methods(request.methods()));
        route.setTimeoutMs(timeoutMs);
        route.setAuthRequired(Texts.or(request.authRequired(), true));
        route.setAllowedRoles(Texts.csv(request.allowedRoles()));
        route.setRateLimit(Texts.or(request.rateLimit(), 60));
        route.setRateWindowSeconds(rateWindow);
        route.setIdempotentPost(Texts.or(request.idempotentPost(), false));
        route.setStripPrefix(Texts.or(request.stripPrefix(), true));
        route.setRequireUserJwt(Texts.or(request.requireUserJwt(), false));
        route.setRequestHeaders(Texts.encodeHeaders(request.requestHeaders()));
        route.setEnabled(Texts.or(request.enabled(), true));
    }

    private void ensurePathAvailable(String publicPath, String currentRouteId) {
        routes.findByPublicPath(publicPath).ifPresent(existing -> {
            if (currentRouteId == null || !existing.getId().equals(currentRouteId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "publicPath is already used");
            }
        });
    }

    private ClientEntity requireClient(String id) {
        ClientEntity client = clients.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not_found", "Client not found"));
        if (!currentAdmin.id().equals(client.getAdminId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Client not found");
        }
        return client;
    }

    private RouteEntity requireRoute(String clientId, String routeId) {
        requireClient(clientId);
        RouteEntity route = routes.findById(routeId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not_found", "Route not found"));
        if (!route.getClientId().equals(clientId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Route not found");
        }
        return route;
    }

    private ClientResponse toClient(ClientEntity client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getApiKey(),
                client.isActive(),
                Texts.split(client.getRoles()),
                client.getCreatedAt());
    }

    private RouteResponse toRoute(RouteEntity route) {
        return new RouteResponse(
                route.getId(),
                route.getClientId(),
                route.getName(),
                route.getPublicPath(),
                route.getTargetUrl(),
                Texts.split(route.getMethods()),
                route.getTimeoutMs(),
                route.isAuthRequired(),
                Texts.split(route.getAllowedRoles()),
                route.getRateLimit(),
                route.getRateWindowSeconds(),
                route.isIdempotentPost(),
                route.isStripPrefix(),
                route.isRequireUserJwt(),
                Texts.decodeHeaders(route.getRequestHeaders()),
                route.isEnabled());
    }

    private RouteSnapshot toSnapshotRoute(RouteEntity route) {
        return new RouteSnapshot(
                route.getId(),
                route.getPublicPath(),
                route.getTargetUrl(),
                Texts.split(route.getMethods()),
                route.getTimeoutMs(),
                route.isAuthRequired(),
                Texts.split(route.getAllowedRoles()),
                route.getRateLimit(),
                route.getRateWindowSeconds(),
                route.isIdempotentPost(),
                route.isStripPrefix(),
                route.isRequireUserJwt(),
                Texts.decodeHeaders(route.getRequestHeaders()),
                route.isEnabled());
    }

    private AuditResponse toAudit(AuditEntry entry) {
        return new AuditResponse(entry.getId(), entry.getAction(), entry.getPath(), entry.getDetail(), entry.getCreatedAt());
    }
}
