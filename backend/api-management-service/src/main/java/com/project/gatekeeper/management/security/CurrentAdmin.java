package com.project.gatekeeper.management.security;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import com.project.gatekeeper.management.repo.OrganizationRepository;
import com.project.gatekeeper.management.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentAdmin {

    public static final String ADMIN = "gatekeeper.admin";

    private final OrganizationRepository organizations;

    public CurrentAdmin(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public AdminEntity require() {
        AdminEntity admin = peek();
        if (admin == null) {
            throw unauthorized();
        }
        return admin;
    }

    public AdminEntity peek() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object value = attributes.getRequest().getAttribute(ADMIN);
        return value instanceof AdminEntity admin ? admin : null;
    }

    public String id() {
        return require().getId();
    }

    public boolean platform() {
        return require().getRole() == AdminRole.PLATFORM_ADMIN;
    }

    public void requirePlatform() {
        if (!platform()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Platform administrators only");
        }
    }

    public OrganizationEntity requireActiveOrganization() {
        AdminEntity admin = require();
        if (admin.getRole() != AdminRole.TENANT_ADMIN || admin.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Tenant administrators only");
        }
        OrganizationEntity organization = organizations.findById(admin.getOrganizationId()).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Organization was not found"));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "org_inactive", "This organization is suspended");
        }
        return organization;
    }

    public static void set(HttpServletRequest request, AdminEntity admin) {
        request.setAttribute(ADMIN, admin);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Login again");
    }
}
