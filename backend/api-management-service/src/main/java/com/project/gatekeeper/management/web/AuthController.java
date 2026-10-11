package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.security.CurrentAdmin;
import com.project.gatekeeper.management.security.OidcLoginCodes;
import com.project.gatekeeper.management.service.AuthService;
import com.project.gatekeeper.management.service.AuthService.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final OidcLoginCodes oidcCodes;
    private final CurrentAdmin currentAdmin;
    private final boolean googleOidcEnabled;
    private final String contactEmail;

    public AuthController(AuthService auth, OidcLoginCodes oidcCodes, CurrentAdmin currentAdmin,
                          @Value("${gatekeeper.oidc.enabled:false}") boolean googleOidcEnabled,
                          @Value("${gatekeeper.contact-email:}") String contactEmail) {
        this.auth = auth;
        this.oidcCodes = oidcCodes;
        this.currentAdmin = currentAdmin;
        this.googleOidcEnabled = googleOidcEnabled;
        this.contactEmail = contactEmail == null ? "" : contactEmail.trim();
    }

    @GetMapping("/options")
    public AuthOptions options() {
        return new AuthOptions(googleOidcEnabled, contactEmail.isBlank() ? null : contactEmail);
    }

    @PostMapping("/register")
    public AuthResult register() {
        throw new ApiException(HttpStatus.FORBIDDEN, "signup_closed",
                "Accounts are created by a Gatekeeper administrator after review");
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public AuthResult me() {
        return auth.view(currentAdmin.require());
    }

    @PostMapping("/oidc/exchange")
    public AuthResult exchange(@Valid @RequestBody OidcExchangeRequest request) {
        if (!googleOidcEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "oidc_disabled", "Google sign-in is not enabled");
        }
        return oidcCodes.consume(request.code());
    }

    public record AuthOptions(boolean googleOidcEnabled, String contactEmail) {
    }

    public record OidcExchangeRequest(@NotBlank String code) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }
}
