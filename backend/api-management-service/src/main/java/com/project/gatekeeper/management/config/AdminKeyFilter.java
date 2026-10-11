package com.project.gatekeeper.management.config;

import com.nimbusds.jwt.JWTClaimsSet;
import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import com.project.gatekeeper.management.repo.AdminRepository;
import com.project.gatekeeper.management.repo.OrganizationRepository;
import com.project.gatekeeper.management.security.CurrentAdmin;
import com.project.gatekeeper.management.security.JwtService;
import com.project.gatekeeper.management.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Key";

    private final String adminApiKey;
    private final JwtService jwt;
    private final AdminRepository admins;
    private final OrganizationRepository organizations;

    public AdminKeyFilter(@Value("${gatekeeper.admin-api-key}") String adminApiKey, JwtService jwt,
                          AdminRepository admins, OrganizationRepository organizations) {
        this.adminApiKey = adminApiKey;
        this.jwt = jwt;
        this.admins = admins;
        this.organizations = organizations;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/favicon.ico")
                || path.equals("/health")
                || path.startsWith("/health/")
                || path.equals("/api/auth/options")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/oidc/exchange")
                || (path.equals("/api/inquiries") && "POST".equalsIgnoreCase(request.getMethod()))
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            if (!serviceKeyMatches(request.getHeader(HEADER))) {
                writeUnauthorized(response, "Send X-Admin-Key");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Login again");
            return;
        }
        try {
            JWTClaimsSet claims = jwt.parse(header.substring("Bearer ".length()).trim());
            AdminEntity admin = admins.findById(claims.getSubject()).orElse(null);
            if (admin == null) {
                writeUnauthorized(response, "Login again");
                return;
            }
            if (admin.getRole() == AdminRole.TENANT_ADMIN) {
                OrganizationEntity organization = admin.getOrganizationId() == null
                        ? null
                        : organizations.findById(admin.getOrganizationId()).orElse(null);
                if (organization == null || organization.getStatus() != OrganizationStatus.ACTIVE) {
                    writeForbidden(response, "This organization is suspended");
                    return;
                }
            }
            CurrentAdmin.set(request, admin);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            writeUnauthorized(response, ex.getMessage());
        }
    }

    private boolean serviceKeyMatches(String presented) {
        return presented != null && MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                adminApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String safe = message == null ? "Login again" : message.replace("\"", "'");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + safe + "\"}");
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        String safe = message == null ? "Forbidden" : message.replace("\"", "'");
        response.getWriter().write("{\"error\":\"org_inactive\",\"message\":\"" + safe + "\"}");
    }
}
