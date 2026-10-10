package com.project.gatekeeper.management.service;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.repo.AdminRepository;
import com.project.gatekeeper.management.security.JwtService;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final AdminRepository admins;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(AdminRepository admins, PasswordEncoder passwords, JwtService jwt) {
        this.admins = admins;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResult register(String name, String email, String password) {
        String normalized = email.trim().toLowerCase();
        if (admins.existsByEmailIgnoreCase(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "An account with that email already exists");
        }
        AdminEntity admin = new AdminEntity();
        admin.setId(UUID.randomUUID().toString());
        admin.setName(name.trim());
        admin.setEmail(normalized);
        admin.setPasswordHash(passwords.encode(password));
        admins.save(admin);
        return result(admin);
    }

    @Transactional(readOnly = true)
    public AuthResult login(String email, String password) {
        AdminEntity admin = admins.findByEmailIgnoreCase(email.trim())
                .filter(found -> passwords.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Email or password is wrong"));
        return result(admin);
    }

    private AuthResult result(AdminEntity admin) {
        return new AuthResult(jwt.issue(admin), new AdminView(admin.getId(), admin.getName(), admin.getEmail()));
    }

    public record AuthResult(String token, AdminView admin) {
    }

    public record AdminView(String id, String name, String email) {
    }
}
