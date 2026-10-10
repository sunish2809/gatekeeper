package com.project.gatekeeper.management.security;

import com.project.gatekeeper.management.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService auth;
    private final OidcLoginCodes codes;
    private final String frontendOrigin;

    public OidcLoginSuccessHandler(AuthService auth, OidcLoginCodes codes,
                                   @Value("${gatekeeper.frontend-origin}") String frontendOrigin) {
        this.auth = auth;
        this.codes = codes;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser user)
                || user.getEmail() == null
                || !Boolean.TRUE.equals(user.getEmailVerified())) {
            response.sendRedirect(errorUrl("Google did not provide a verified email address"));
            return;
        }
        String name = user.getFullName();
        String code = codes.issue(auth.loginOrRegisterOidc(name, user.getEmail()));
        response.sendRedirect(frontendOrigin + "/?oidc_code="
                + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    private String errorUrl(String message) {
        return frontendOrigin + "/?oidc_error=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }
}
