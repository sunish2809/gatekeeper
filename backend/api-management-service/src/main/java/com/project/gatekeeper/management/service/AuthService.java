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
    public AuthResult login(String email, String password) {
        AdminEntity admin = admins.findByEmailIgnoreCase(email.trim())
                .filter(found -> passwords.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Email or password is wrong"));
        ensureCanSignIn(admin);
        return result(admin);
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
