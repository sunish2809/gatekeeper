package com.project.gatekeeper.management.security;

import com.project.gatekeeper.management.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentAdmin {

    public static final String ADMIN_ID = "gatekeeper.adminId";

    public String id() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw unauthorized();
        }
        Object adminId = attributes.getRequest().getAttribute(ADMIN_ID);
        if (adminId == null || adminId.toString().isBlank()) {
            throw unauthorized();
        }
        return adminId.toString();
    }

    public static void set(HttpServletRequest request, String adminId) {
        request.setAttribute(ADMIN_ID, adminId);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Login again");
    }
}
