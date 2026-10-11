package com.project.gatekeeper.management.service;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.domain.ClientEntity;
import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import com.project.gatekeeper.management.repo.AdminRepository;
import com.project.gatekeeper.management.repo.ClientRepository;
import com.project.gatekeeper.management.repo.RouteRepository;
import com.project.gatekeeper.management.repo.OrganizationRepository;
import com.project.gatekeeper.management.security.CurrentAdmin;
import com.project.gatekeeper.management.support.Texts;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final AdminRepository admins;
    private final ClientRepository clients;
    private final RouteRepository routes;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final CurrentAdmin currentAdmin;

    public OrganizationService(OrganizationRepository organizations, AdminRepository admins, ClientRepository clients,
                               RouteRepository routes, PasswordEncoder passwords, ApplicationEventPublisher events,
                               CurrentAdmin currentAdmin) {
        this.organizations = organizations;
        this.admins = admins;
        this.clients = clients;
        this.routes = routes;
        this.passwords = passwords;
        this.events = events;
        this.currentAdmin = currentAdmin;
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        currentAdmin.requirePlatform();
        String slug = Texts.slug(request.slug());
        organizations.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (existing.getStatus() == OrganizationStatus.DELETED) {
                retireSlug(existing);
                retireTenantEmails(existing);
                organizations.save(existing);
            } else {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "That slug is already used");
            }
        });
        String email = request.adminEmail().trim().toLowerCase();
        if (admins.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "An account with that email already exists");
        }
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(UUID.randomUUID().toString());
        organization.setName(request.name().trim());
        organization.setSlug(slug);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizations.save(organization);

        AdminEntity tenant = new AdminEntity();
        tenant.setId(UUID.randomUUID().toString());
        tenant.setName(request.adminName().trim());
        tenant.setEmail(email);
        tenant.setPasswordHash(passwords.encode(request.adminPassword()));
        tenant.setRole(AdminRole.TENANT_ADMIN);
        tenant.setOrganizationId(organization.getId());
        admins.save(tenant);

        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID().toString());
        client.setAdminId(tenant.getId());
        client.setOrganizationId(organization.getId());
        client.setName(organization.getName());
        client.setApiKey("gk_" + UUID.randomUUID().toString().replace("-", ""));
        client.setActive(true);
        clients.save(client);

        events.publishEvent(new ConfigChanged(client.getId(), "organization.created"));
        return toResponse(organization, tenant, client);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(boolean includeDeleted) {
        currentAdmin.requirePlatform();
        List<OrganizationEntity> rows = includeDeleted
                ? organizations.findAllByOrderByCreatedAtDesc()
                : organizations.findByStatusNotOrderByCreatedAtDesc(OrganizationStatus.DELETED);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(String id) {
        currentAdmin.requirePlatform();
        return toResponse(requireOrg(id));
    }

    @Transactional
    public OrganizationResponse activate(String id) {
        currentAdmin.requirePlatform();
        OrganizationEntity organization = requireOrg(id);
        if (organization.getStatus() == OrganizationStatus.DELETED) {
            organization.setDeletedAt(null);
            restoreSlug(organization);
            restoreTenantEmails(organization);
        }
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizations.save(organization);
        publishOrg(organization, "organization.activated");
        return toResponse(organization);
    }

    @Transactional
    public OrganizationResponse suspend(String id) {
        currentAdmin.requirePlatform();
        OrganizationEntity organization = requireOrg(id);
        if (organization.getStatus() == OrganizationStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_org", "Restore a deleted organization before suspending it");
        }
        organization.setStatus(OrganizationStatus.SUSPENDED);
        organizations.save(organization);
        publishOrg(organization, "organization.suspended");
        return toResponse(organization);
    }

    @Transactional
    public OrganizationResponse softDelete(String id) {
        currentAdmin.requirePlatform();
        OrganizationEntity organization = requireOrg(id);
        organization.setStatus(OrganizationStatus.DELETED);
        organization.setDeletedAt(Instant.now());
        retireSlug(organization);
        retireTenantEmails(organization);
        organizations.save(organization);
        publishOrg(organization, "organization.deleted");
        return toResponse(organization);
    }

    private void retireSlug(OrganizationEntity organization) {
        String slug = organization.getSlug();
        String marker = "--x--" + organization.getId();
        if (slug != null && !slug.contains("--x--")) {
            organization.setSlug(slug + marker);
        }
    }

    private void restoreSlug(OrganizationEntity organization) {
        String slug = organization.getSlug();
        String marker = "--x--" + organization.getId();
        if (slug == null || !slug.contains("--x--")) {
            return;
        }
        String original = slug.endsWith(marker)
                ? slug.substring(0, slug.length() - marker.length())
                : slug.substring(0, slug.indexOf("--x--"));
        if (original.isBlank()) {
            return;
        }
        if (organizations.existsBySlugIgnoreCaseAndStatusNot(original, OrganizationStatus.DELETED)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Cannot restore this organization because slug /" + original + " is already used");
        }
        organization.setSlug(original);
    }

    private void retireTenantEmails(OrganizationEntity organization) {
        String prefix = "deleted." + organization.getId() + ".";
        for (AdminEntity admin : admins.findByOrganizationId(organization.getId())) {
            if (admin.getEmail() != null && !admin.getEmail().startsWith("deleted.")) {
                admin.setEmail(prefix + admin.getEmail());
                admins.save(admin);
            }
        }
    }

    private void restoreTenantEmails(OrganizationEntity organization) {
        String prefix = "deleted." + organization.getId() + ".";
        for (AdminEntity admin : admins.findByOrganizationId(organization.getId())) {
            if (admin.getEmail() != null && admin.getEmail().startsWith(prefix)) {
                admin.setEmail(admin.getEmail().substring(prefix.length()));
                admins.save(admin);
            }
        }
    }

    private void publishOrg(OrganizationEntity organization, String reason) {
        clients.findByOrganizationId(organization.getId()).forEach(client ->
                events.publishEvent(new ConfigChanged(client.getId(), reason)));
    }

    private OrganizationEntity requireOrg(String id) {
        return organizations.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not_found", "Organization not found"));
    }

    private OrganizationResponse toResponse(OrganizationEntity organization) {
        AdminEntity tenant = admins.findByOrganizationId(organization.getId()).stream()
                .filter(admin -> admin.getRole() == AdminRole.TENANT_ADMIN)
                .findFirst()
                .orElse(null);
        ClientEntity client = clients.findByOrganizationId(organization.getId()).stream().findFirst().orElse(null);
        return toResponse(organization, tenant, client);
    }

    private OrganizationResponse toResponse(OrganizationEntity organization, AdminEntity tenant, ClientEntity client) {
        int routeCount = client == null ? 0 : routes.findByClientId(client.getId()).size();
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getStatus().name(),
                organization.getCreatedAt(),
                organization.getDeletedAt(),
                tenant == null ? null : new TenantAdminView(tenant.getId(), tenant.getName(), tenant.getEmail()),
                client == null ? null : client.getId(),
                routeCount);
    }

    public record CreateOrganizationRequest(
            String name,
            String slug,
            String adminName,
            String adminEmail,
            String adminPassword
    ) {
    }

    public record TenantAdminView(String id, String name, String email) {
    }

    public record OrganizationResponse(
            String id,
            String name,
            String slug,
            String status,
            Instant createdAt,
            Instant deletedAt,
            TenantAdminView admin,
            String clientId,
            int routeCount
    ) {
    }
}
