package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.security.OidcLoginCodes;
import com.project.gatekeeper.management.service.AuthService;
import com.project.gatekeeper.management.service.AuthService.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean googleOidcEnabled;

    public AuthController(AuthService auth, OidcLoginCodes oidcCodes,
                          @Value("${gatekeeper.oidc.enabled:false}") boolean googleOidcEnabled) {
        this.auth = auth;
        this.oidcCodes = oidcCodes;
        this.googleOidcEnabled = googleOidcEnabled;
    }

    @GetMapping("/options")
    public AuthOptions options() {
        return new AuthOptions(googleOidcEnabled);
    }

    @PostMapping("/register")
    public AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/oidc/exchange")
    public AuthResult exchange(@Valid @RequestBody OidcExchangeRequest request) {
        if (!googleOidcEnabled) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "oidc_disabled", "Google sign-in is not enabled");
        }
        return oidcCodes.consume(request.code());
    }

    public record AuthOptions(boolean googleOidcEnabled) {
    }

    public record OidcExchangeRequest(@NotBlank String code) {
    }

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }
}
