package com.project.gatekeeper.management;

import com.project.gatekeeper.management.security.OidcLoginCodes;
import com.project.gatekeeper.management.service.AuthService.AdminView;
import com.project.gatekeeper.management.service.AuthService.AuthResult;
import com.project.gatekeeper.management.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcLoginCodesTest {

    @Test
    void codeCanOnlyBeConsumedOnce() {
        OidcLoginCodes codes = new OidcLoginCodes();
        AuthResult expected = new AuthResult("jwt", new AdminView("id", "Ada", "ada@example.com"));

        String code = codes.issue(expected);

        assertEquals(expected, codes.consume(code));
        assertThrows(ApiException.class, () -> codes.consume(code));
    }
}
