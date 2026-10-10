package com.project.gatekeeper.management.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OidcLoginFailureHandler implements AuthenticationFailureHandler {

    private final String frontendOrigin;

    public OidcLoginFailureHandler(@Value("${gatekeeper.frontend-origin}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message = "Google sign-in could not be completed";
        response.sendRedirect(frontendOrigin + "/?oidc_error="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
