package com.project.gatekeeper.management.config;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.repo.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final AdminRepository admins;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String name;

    public PlatformAdminBootstrap(AdminRepository admins, PasswordEncoder passwords,
                                  @Value("${gatekeeper.platform.email:}") String email,
                                  @Value("${gatekeeper.platform.password:}") String password,
                                  @Value("${gatekeeper.platform.name:Platform Admin}") String name) {
        this.admins = admins;
        this.passwords = passwords;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.existsByRole(AdminRole.PLATFORM_ADMIN)) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No platform administrator exists. Set GATEKEEPER_PLATFORM_EMAIL and GATEKEEPER_PLATFORM_PASSWORD.");
            return;
        }
        if (admins.existsByEmailIgnoreCase(email)) {
            AdminEntity existing = admins.findByEmailIgnoreCase(email).orElseThrow();
            existing.setRole(AdminRole.PLATFORM_ADMIN);
            existing.setOrganizationId(null);
            admins.save(existing);
            log.info("Promoted {} to platform administrator", email);
            return;
        }
        AdminEntity admin = new AdminEntity();
        admin.setId(UUID.randomUUID().toString());
        admin.setName(name == null || name.isBlank() ? "Platform Admin" : name.trim());
        admin.setEmail(email);
        admin.setPasswordHash(passwords.encode(password));
        admin.setRole(AdminRole.PLATFORM_ADMIN);
        admins.save(admin);
        log.info("Created the first platform administrator {}", email);
    }
}
