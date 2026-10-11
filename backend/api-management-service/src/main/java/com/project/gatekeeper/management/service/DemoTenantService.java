package com.project.gatekeeper.management.service;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.domain.ClientEntity;
import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import com.project.gatekeeper.management.domain.RouteEntity;
import com.project.gatekeeper.management.repo.AdminRepository;
import com.project.gatekeeper.management.repo.ClientRepository;
import com.project.gatekeeper.management.repo.OrganizationRepository;
import com.project.gatekeeper.management.repo.RouteRepository;
import com.project.gatekeeper.management.kafka.FlatJson;
import com.project.gatekeeper.management.support.Texts;
import com.project.gatekeeper.management.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoTenantService {

    private static final Logger log = LoggerFactory.getLogger(DemoTenantService.class);

    private final boolean enabled;
    private final String email;
    private final String password;
    private final String adminName;
    private final String orgName;
    private final String slug;
    private final OrganizationRepository organizations;
    private final AdminRepository admins;
    private final ClientRepository clients;
    private final RouteRepository routes;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final AuthService auth;
    private final KafkaTemplate<String, String> kafka;
    private final String accessTopic;

    public DemoTenantService(
            @Value("${gatekeeper.demo.enabled:true}") boolean enabled,
            @Value("${gatekeeper.demo.email:priya@demo.gatekeeper}") String email,
            @Value("${gatekeeper.demo.password:demo-pass}") String password,
            @Value("${gatekeeper.demo.admin-name:Priya Sharma}") String adminName,
            @Value("${gatekeeper.demo.org-name:Demo Kitchen}") String orgName,
            @Value("${gatekeeper.demo.slug:demo}") String slug,
            OrganizationRepository organizations,
            AdminRepository admins,
            ClientRepository clients,
            RouteRepository routes,
            PasswordEncoder passwords,
            ApplicationEventPublisher events,
            AuthService auth,
            KafkaTemplate<String, String> kafka,
            @Value("${gatekeeper.access-topic:gateway.access.events}") String accessTopic
    ) {
        this.enabled = enabled;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
        this.adminName = adminName;
        this.orgName = orgName;
        this.slug = slug;
        this.organizations = organizations;
        this.admins = admins;
        this.clients = clients;
        this.routes = routes;
        this.passwords = passwords;
        this.events = events;
        this.auth = auth;
        this.kafka = kafka;
        this.accessTopic = accessTopic;
    }

    public boolean enabled() {
        return enabled && !email.isBlank() && !password.isBlank();
    }

    @Transactional
    public void ensurePresent() {
        if (!enabled()) {
            return;
        }
        OrganizationEntity organization = organizations.findBySlugIgnoreCase(slug).orElse(null);
        if (organization == null) {
            if (admins.existsByEmailIgnoreCase(email)) {
                log.warn("Demo email {} already exists, skipping seed", email);
                return;
            }
            organization = createDemo();
        }
        List<ClientEntity> orgClients = clients.findByOrganizationId(organization.getId());
        if (orgClients.isEmpty()) {
            return;
        }
        ClientEntity client = orgClients.get(0);
        addRoute(client.getId(), organization.getSlug(), "Echo API", "/order");
        addRoute(client.getId(), organization.getSlug(), "Pay API", "/pay");
        seedTraffic(client);
    }

    private OrganizationEntity createDemo() {
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(UUID.randomUUID().toString());
        organization.setName(orgName == null || orgName.isBlank() ? "Demo Kitchen" : orgName.trim());
        organization.setSlug(Texts.slug(slug));
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizations.save(organization);

        AdminEntity tenant = new AdminEntity();
        tenant.setId(UUID.randomUUID().toString());
        tenant.setName(adminName == null || adminName.isBlank() ? "Priya Sharma" : adminName.trim());
        tenant.setEmail(email);
        tenant.setPasswordHash(passwords.encode(password));
        tenant.setRole(AdminRole.TENANT_ADMIN);
        tenant.setOrganizationId(organization.getId());
        admins.save(tenant);

        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID().toString());
        client.setAdminId(tenant.getId());
        client.setOrganizationId(organization.getId());
        client.setName(organization.getName());
        client.setApiKey("gk_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        client.setActive(true);
        clients.save(client);

        addRoute(client.getId(), organization.getSlug(), "Echo API", "/order");
        addRoute(client.getId(), organization.getSlug(), "Pay API", "/pay");
        events.publishEvent(new ConfigChanged(client.getId(), "demo.seeded"));
        log.info("Seeded shared demo tenant {} /{}", email, organization.getSlug());
        return organization;
    }

    private void addRoute(String clientId, String orgSlug, String name, String relativePath) {
        String publicPath = Texts.orgPublicPath(orgSlug, relativePath);
        if (routes.findByPublicPath(publicPath).isPresent()) {
            return;
        }
        RouteEntity route = new RouteEntity();
        route.setId(UUID.randomUUID().toString());
        route.setClientId(clientId);
        route.setName(name);
        route.setPublicPath(publicPath);
        route.setTargetUrl("https://httpbin.org/anything");
        route.setMethods("GET,POST");
        route.setTimeoutMs(5000);
        route.setAuthRequired(false);
        route.setRateLimit(30);
        route.setRateWindowSeconds(60);
        route.setIdempotentPost(false);
        route.setStripPrefix(true);
        route.setRequireUserJwt(false);
        route.setRequestHeaders(Texts.encodeHeaders(Map.of("X-Source", "gatekeeper")));
        route.setAllowedOrigins("*");
        route.setEnabled(true);
        routes.save(route);
    }

    private void seedTraffic(ClientEntity client) {
        List<RouteEntity> demoRoutes = routes.findByClientId(client.getId());
        if (demoRoutes.isEmpty()) {
            return;
        }
        RouteEntity order = demoRoutes.stream()
                .filter(route -> route.getPublicPath() != null && route.getPublicPath().endsWith("/order"))
                .findFirst()
                .orElse(demoRoutes.get(0));
        RouteEntity pay = demoRoutes.stream()
                .filter(route -> route.getPublicPath() != null && route.getPublicPath().endsWith("/pay"))
                .findFirst()
                .orElse(order);
        Instant now = Instant.now();
        Object[][] samples = {
                { "GET", order, 200, 48L, null },
                { "GET", order, 200, 62L, null },
                { "GET", order, 200, 71L, null },
                { "POST", order, 201, 118L, null },
                { "GET", order, 200, 55L, null },
                { "GET", pay, 200, 88L, null },
                { "GET", order, 401, 12L, "invalid_user_token" },
                { "GET", order, 200, 44L, null },
                { "POST", pay, 201, 140L, null },
                { "GET", order, 429, 9L, "rate_limited" },
                { "GET", order, 200, 51L, null },
                { "GET", pay, 200, 93L, null },
                { "POST", order, 201, 102L, null },
                { "GET", order, 504, 5008L, "timeout" },
                { "GET", order, 200, 39L, null },
                { "GET", order, 200, 67L, null }
        };
        for (int i = 0; i < 48; i++) {
            Object[] sample = samples[i % samples.length];
            RouteEntity route = (RouteEntity) sample[1];
            Instant at = now.minus(50 - i, ChronoUnit.MINUTES).minus(i * 17L, ChronoUnit.SECONDS);
            publishAccess(client.getId(), route, (String) sample[0], (int) sample[2], (long) sample[3],
                    (String) sample[4], at, i);
        }
        log.info("Published demo traffic samples for client {}", client.getId());
    }

    private void publishAccess(String clientId, RouteEntity route, String method, int status, long latencyMs,
                               String error, Instant at, int index) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "demo-seed-" + index);
        event.put("correlationId", "demo-corr-" + index);
        event.put("clientId", clientId);
        event.put("routeId", route.getId());
        event.put("method", method);
        event.put("path", route.getPublicPath());
        event.put("status", status);
        event.put("latencyMs", latencyMs);
        event.put("timestamp", at.toString());
        event.put("error", error);
        try {
            kafka.send(accessTopic, clientId, FlatJson.write(event));
        } catch (RuntimeException ex) {
            log.warn("Demo access event {} was not published: {}", index, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AuthService.AuthResult login() {
        if (!enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "demo_disabled", "The shared demo is turned off");
        }
        AdminEntity admin = admins.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "demo_unavailable",
                        "The shared demo is not ready yet"));
        return auth.view(admin);
    }
}
