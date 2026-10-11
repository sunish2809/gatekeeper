package com.project.gatekeeper.management.service;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import com.project.gatekeeper.management.repo.AdminRepository;
import com.project.gatekeeper.management.repo.OrganizationRepository;
import com.project.gatekeeper.management.security.JwtService;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdminRepository admins;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(AdminRepository admins, OrganizationRepository organizations, PasswordEncoder passwords,
                       JwtService jwt) {
        this.admins = admins;
        this.organizations = organizations;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public AuthResult login(String email, String password, AdminRole portal) {
        AdminEntity admin = admins.findByEmailIgnoreCase(email.trim())
                .filter(found -> passwords.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Email or password is wrong"));
        ensurePortal(admin, portal);
        ensureCanSignIn(admin);
        return result(admin);
    }

    public static AdminRole parsePortal(String portal) {
        if (portal == null || portal.isBlank()) {
            return null;
        }
        String value = portal.trim().toUpperCase();
        if ("PLATFORM".equals(value) || "PLATFORM_ADMIN".equals(value)) {
            return AdminRole.PLATFORM_ADMIN;
        }
        if ("TENANT".equals(value) || "TENANT_ADMIN".equals(value) || "ORGANIZATION".equals(value)) {
            return AdminRole.TENANT_ADMIN;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_portal", "portal must be PLATFORM or TENANT");
    }

    @Transactional(readOnly = true)
    public AuthResult loginExistingOidc(String name, String email) {
        String normalized = email.trim().toLowerCase();
        AdminEntity admin = admins.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "not_provisioned",
                        "This Google account is not provisioned. Ask a Gatekeeper administrator."));
        if (name != null && !name.isBlank() && (admin.getName() == null || admin.getName().isBlank())) {
            admin.setName(name.trim());
            admins.save(admin);
        }
        ensureCanSignIn(admin);
        return result(admin);
    }

    @Transactional(readOnly = true)
    public AuthResult view(AdminEntity admin) {
        ensureCanSignIn(admin);
        return result(admin);
    }

    public AuthResult result(AdminEntity admin) {
        OrganizationEntity organization = admin.getOrganizationId() == null
                ? null
                : organizations.findById(admin.getOrganizationId()).orElse(null);
        return new AuthResult(jwt.issue(admin), toView(admin, organization));
    }

    public static AdminView toView(AdminEntity admin, OrganizationEntity organization) {
        return new AdminView(
                admin.getId(),
                admin.getName(),
                admin.getEmail(),
                admin.getRole() == null ? AdminRole.PLATFORM_ADMIN.name() : admin.getRole().name(),
                organization == null ? null : organization.getId(),
                organization == null ? null : organization.getName(),
                organization == null ? null : organization.getSlug(),
                organization == null ? null : organization.getStatus().name());
    }

    private void ensurePortal(AdminEntity admin, AdminRole portal) {
        if (portal == null) {
            return;
        }
        AdminRole actual = admin.getRole() == null ? AdminRole.PLATFORM_ADMIN : admin.getRole();
        if (actual == portal) {
            return;
        }
        if (portal == AdminRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "wrong_portal",
                    "This account is a SaaS administrator login. Use the organization sign-in page.");
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "wrong_portal",
                "This account is a Gatekeeper platform login. Use the platform admin page.");
    }

    private void ensureCanSignIn(AdminEntity admin) {
        if (admin.getRole() != AdminRole.TENANT_ADMIN) {
            return;
        }
        if (admin.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "org_inactive", "This account is not linked to an organization");
        }
        OrganizationEntity organization = organizations.findById(admin.getOrganizationId()).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "org_inactive", "This organization was not found"));
        if (organization.getStatus() == OrganizationStatus.DELETED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "org_inactive", "This organization is no longer active");
        }
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "org_inactive", "This organization is suspended");
        }
    }

    public record AuthResult(String token, AdminView admin) {
    }

    public record AdminView(
            String id,
            String name,
            String email,
            String role,
            String organizationId,
            String organizationName,
            String organizationSlug,
            String organizationStatus
    ) {
    }
}
