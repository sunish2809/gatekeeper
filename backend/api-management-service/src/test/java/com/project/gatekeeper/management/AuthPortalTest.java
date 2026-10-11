package com.project.gatekeeper.management;

import com.project.gatekeeper.management.domain.AdminRole;
import com.project.gatekeeper.management.service.AuthService;
import com.project.gatekeeper.management.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthPortalTest {

    @Test
    void parsesPortalOrLeavesUnchecked() {
        assertNull(AuthService.parsePortal(null));
        assertNull(AuthService.parsePortal(" "));
        assertEquals(AdminRole.PLATFORM_ADMIN, AuthService.parsePortal("platform"));
        assertEquals(AdminRole.TENANT_ADMIN, AuthService.parsePortal("TENANT"));
        assertThrows(ApiException.class, () -> AuthService.parsePortal("guest"));
    }
}
